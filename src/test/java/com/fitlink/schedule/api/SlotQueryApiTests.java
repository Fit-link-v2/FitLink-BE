package com.fitlink.schedule.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.infra.SessionRepository;
import com.fitlink.schedule.ScheduleFixtures;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울), 오픈 범위는 14일이다
class SlotQueryApiTests extends IntegrationTest {

	static final int WEDNESDAY = 3;

	@Autowired
	SessionRepository sessions;

	ScheduleFixtures given;

	long instructorId;

	Cookie session;

	@BeforeEach
	void 로그인한_강사() {
		given = new ScheduleFixtures(jdbc);
		instructorId = given.instructor("1001");
		session = new Cookie("SESSION", sessions.issue(instructorId));
	}

	@Test
	void 목록은_지금_이후에_시작하는_자기_슬롯만_시각_순으로_주고_오픈_범위를_같이_준다() throws Exception {
		long later = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);
		long sooner = given.slot(instructorId, null, "2026-11-12", "2026-11-12T12:00:01+09:00", null);
		given.slot(instructorId, null, "2026-11-12", "2026-11-12T12:00:00+09:00", null);
		given.slot(instructorId, null, "2026-11-12", "2026-11-12T11:00:00+09:00", null);
		given.slot(instructorId, null, "2026-11-11", "2026-11-11T20:00:00+09:00", null);
		given.slot(given.instructor("2002"), null, "2026-11-13", "2026-11-13T10:00:00+09:00", null);

		slots().andExpect(status().isOk())
			.andExpect(jsonPath("$.openRangeDays").value(14))
			.andExpect(jsonPath("$.items[*].id", contains((int) sooner, (int) later)));
	}

	@Test
	void 목록의_슬롯은_서울_시각과_정원_찬_인원을_준다() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);
		jdbc.sql("UPDATE class_slot SET taken = 3 WHERE id = :id").param("id", slot).update();

		slots().andExpect(jsonPath("$.items[0].id").value(slot))
			.andExpect(jsonPath("$.items[0].startsAt").value("2026-11-13T09:00:00+09:00"))
			.andExpect(jsonPath("$.items[0].capacity").value(8))
			.andExpect(jsonPath("$.items[0].taken").value(3))
			.andExpect(jsonPath("$.items[0].canceledAt").value(nullValue()));
	}

	@Test
	void 휴강한_슬롯과_오픈_범위_밖에_남은_슬롯도_목록에_있다() throws Exception {
		long canceled = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00",
				"2026-11-12T10:00:00+09:00");
		long outside = given.slot(instructorId, null, "2026-12-20", "2026-12-20T09:00:00+09:00", null);

		slots().andExpect(jsonPath("$.items[*].id", contains((int) canceled, (int) outside)))
			.andExpect(jsonPath("$.items[0].canceledAt").value("2026-11-12T10:00:00+09:00"))
			.andExpect(jsonPath("$.items[1].canceledAt").value(nullValue()));
	}

	@Test
	void 목록의_오픈_범위는_강사의_설정값이다() throws Exception {
		jdbc.sql("UPDATE setting SET open_range_days = 21 WHERE instructor_id = :id").param("id", instructorId).update();

		slots().andExpect(jsonPath("$.openRangeDays").value(21)).andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void 반복_회차의_상세는_규칙의_요일과_시각을_준다() throws Exception {
		long rule = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, true);
		long slot = given.slot(instructorId, rule, "2026-11-18", "2026-11-18T19:30:00+09:00", null);

		detail(slot).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(slot))
			.andExpect(jsonPath("$.startsAt").value("2026-11-18T19:30:00+09:00"))
			.andExpect(jsonPath("$.capacity").value(8))
			.andExpect(jsonPath("$.taken").value(0))
			.andExpect(jsonPath("$.canceledAt").value(nullValue()))
			.andExpect(jsonPath("$.recurrence.id").value(rule))
			.andExpect(jsonPath("$.recurrence.weekday").value("WED"))
			.andExpect(jsonPath("$.recurrence.startTime").value("20:00"));
	}

	@Test
	void 일회성_수업의_상세는_recurrence가_null이고_지난_슬롯도_볼_수_있다() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-11", "2026-11-11T20:00:00+09:00", null);

		detail(slot).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(slot))
			.andExpect(jsonPath("$.recurrence").value(nullValue()));
	}

	@Test
	void 다른_강사의_슬롯이나_없는_id의_상세는_404() throws Exception {
		long others = given.slot(given.instructor("2002"), null, "2026-11-13", "2026-11-13T10:00:00+09:00", null);

		detail(others).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		detail(others + 100).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 상세_경로의_id가_숫자가_아니면_400() throws Exception {
		mockMvc.perform(get("/instructor/slots/abc").cookie(session))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields[0].name").value("id"));
	}

	private ResultActions slots() throws Exception {
		return mockMvc.perform(get("/instructor/slots").cookie(session));
	}

	private ResultActions detail(long id) throws Exception {
		return mockMvc.perform(get("/instructor/slots/{id}", id).cookie(session));
	}

}
