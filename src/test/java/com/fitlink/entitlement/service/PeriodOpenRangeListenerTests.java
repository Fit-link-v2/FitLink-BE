package com.fitlink.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.entitlement.EntitlementFixtures;
import com.fitlink.instructor.InstructorOpenRangeExtendedEvent;
import com.fitlink.membership.MembershipFixtures;
import com.fitlink.schedule.ScheduleFixtures;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울)이다. 오픈 범위를 14 → 28로 늘리면 [11-12, 12-10)이 된다
@ExtendWith(OutputCaptureExtension.class)
class PeriodOpenRangeListenerTests extends IntegrationTest {

	static final int THURSDAY = 4;

	// 주기 INSERT 중 한 월 정액의 것만 실패시키는 제약. NOT VALID라 이미 있는 행은 검사하지 않는다.
	// TRUNCATE는 제약을 지우지 않으므로 테스트가 중간에 멈춰도 다음 테스트 전에 지운다
	static final String BLOCK_INSERT = "period_open_range_tests_block_insert";

	@Autowired
	SessionRepository sessions;

	@Autowired
	ApplicationEventPublisher events;

	MembershipFixtures members;

	EntitlementFixtures given;

	long instructorId;

	Cookie session;

	@BeforeEach
	void 로그인한_강사() {
		dropBlockInsert();
		members = new MembershipFixtures(jdbc);
		given = new EntitlementFixtures(jdbc);
		instructorId = members.instructor("1001");
		session = new Cookie("SESSION", sessions.issue(instructorId));
	}

	@AfterEach
	void 정리() {
		dropBlockInsert();
	}

	@Test
	void 오픈_범위를_늘리면_응답_전에_늘어난_범위까지_월_정액_주기가_생긴다() throws Exception {
		long id = subscriptionOf(instructorId, "2026-11-12", 8);
		given.period(id, memberOf(id), "2026-11-12", "2026-11-18", 2);
		given.period(id, memberOf(id), "2026-11-19", "2026-11-25", 2);

		change(28).andExpect(status().isOk()).andExpect(jsonPath("$.openRangeDays").value(28));

		assertThat(periods(id)).containsExactly("2026-11-12", "2026-11-19", "2026-11-26", "2026-12-03");
	}

	@Test
	void 다른_강사의_월_정액_주기는_만들지_않는다() throws Exception {
		long othersSubscription = subscriptionOf(members.instructor("1002"), "2026-11-12", 8);

		change(28).andExpect(status().isOk());

		assertThat(periods(othersSubscription)).isEmpty();
	}

	@Test
	void 한_건이_실패해도_그_건만_되돌리고_오픈_범위_회차_다른_건_주기는_저장된다(CapturedOutput output) throws Exception {
		new ScheduleFixtures(jdbc).recurrence(instructorId, THURSDAY, "20:00", 8, true);
		long failing = subscriptionOf(instructorId, "2026-11-12", 8);
		long other = subscriptionOf(instructorId, "2026-11-12", 8);
		jdbc.sql("ALTER TABLE entitlement ADD CONSTRAINT " + BLOCK_INSERT + " CHECK (source_id IS DISTINCT FROM "
				+ failing + ") NOT VALID").update();

		change(28).andExpect(status().isOk());

		assertThat(jdbc.sql("SELECT open_range_days FROM setting WHERE instructor_id = :id")
			.param("id", instructorId)
			.query(Integer.class)
			.single()).isEqualTo(28);
		assertThat(jdbc.sql("SELECT occurs_on::text FROM class_slot ORDER BY occurs_on").query(String.class).list())
			.containsExactly("2026-11-12", "2026-11-19", "2026-11-26", "2026-12-03");
		assertThat(periods(failing)).isEmpty();
		assertThat(periods(other)).containsExactly("2026-11-12", "2026-11-19", "2026-11-26", "2026-12-03");
		assertThat(output.getAll()).contains("월 정액 " + failing + " 주기 생성 실패");
	}

	// 시계는 11-12 12:00이지만 이벤트는 11-19를 싣는다. 시계를 다시 읽으면 11-12 주기도 생긴다
	@Test
	void 이벤트에_실린_오늘을_기준으로_만든다() {
		long id = subscriptionOf(instructorId, "2026-11-12", 8);

		events.publishEvent(new InstructorOpenRangeExtendedEvent(instructorId, LocalDate.parse("2026-11-19"),
				OffsetDateTime.parse("2026-11-19T20:00:00+09:00")));

		assertThat(periods(id)).containsExactly("2026-11-19", "2026-11-26");
	}

	// 회원 1명에 주당 2회 월 정액 1건
	private long subscriptionOf(long instructor, String startsOn, int weeks) {
		long memberId = members.member(instructor, "김OO", "ACTIVE");
		return given.subscription(memberId, "WEEK", 2, startsOn, weeks);
	}

	private long memberOf(long subscriptionId) {
		return jdbc.sql("SELECT member_id FROM subscription WHERE id = :id")
			.param("id", subscriptionId)
			.query(Long.class)
			.single();
	}

	private ResultActions change(int openRangeDays) throws Exception {
		return mockMvc.perform(patch("/instructor/settings").header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"openRangeDays\": " + openRangeDays + " }"));
	}

	private List<String> periods(long subscriptionId) {
		return jdbc.sql("SELECT window_start::text FROM entitlement WHERE source_id = :id ORDER BY window_start")
			.param("id", subscriptionId)
			.query(String.class)
			.list();
	}

	private void dropBlockInsert() {
		jdbc.sql("ALTER TABLE entitlement DROP CONSTRAINT IF EXISTS " + BLOCK_INSERT).update();
	}

}
