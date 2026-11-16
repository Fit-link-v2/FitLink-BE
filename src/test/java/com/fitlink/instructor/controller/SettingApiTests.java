package com.fitlink.instructor.controller;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.schedule.ScheduleFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울)이다
class SettingApiTests extends IntegrationTest {

	static final int THURSDAY = 4;

	// 회차 INSERT만 실패시키는 제약. NOT VALID라 이미 있는 행은 검사하지 않는다.
	// TRUNCATE는 제약을 지우지 않으므로 테스트가 중간에 멈춰도 다음 테스트 전에 지운다
	static final String BLOCK_INSERT = "setting_api_tests_block_insert";

	@Autowired
	SessionRepository sessions;

	ScheduleFixtures given;

	long instructorId;

	Cookie session;

	@BeforeEach
	void 로그인한_강사() {
		dropBlockInsert();
		given = new ScheduleFixtures(jdbc);
		instructorId = given.instructor("1001");
		session = new Cookie("SESSION", sessions.issue(instructorId));
	}

	@Test
	void GET은_오픈_범위_기본값_14() throws Exception {
		mockMvc.perform(get("/instructor/settings").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.openRangeDays").value(14));
	}

	@Test
	void PATCH는_오픈_범위를_바꾸고_바꾼_값을_돌려준다() throws Exception {
		change("{ \"openRangeDays\": 21 }").andExpect(status().isOk())
			.andExpect(jsonPath("$.openRangeDays").value(21));

		assertThat(openRangeDays()).isEqualTo(21);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "{ \"openRangeDays\": 6 }", "{ \"openRangeDays\": 29 }", "{ }" })
	void 범위가_7_28_밖이거나_값이_없으면_400이고_그대로(String body) throws Exception {
		change(body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value("openRangeDays"));

		assertThat(openRangeDays()).isEqualTo(14);
	}

	@Test
	void 늘리면_응답_전에_늘어난_구간의_회차가_생긴다() throws Exception {
		given.recurrence(instructorId, THURSDAY, "20:00", 8, true);

		change("{ \"openRangeDays\": 28 }").andExpect(status().isOk());

		assertThat(slotDays()).containsExactly("2026-11-12", "2026-11-19", "2026-11-26", "2026-12-03");
	}

	@ParameterizedTest(name = "28 → {0}")
	@ValueSource(ints = { 7, 28 })
	void 줄이거나_그대로면_회차를_만들지_않고_범위_밖_슬롯도_남는다(int days) throws Exception {
		jdbc.sql("UPDATE setting SET open_range_days = 28").update();
		long rule = given.recurrence(instructorId, THURSDAY, "20:00", 8, true);
		given.slot(instructorId, rule, "2026-12-03", "2026-12-03T20:00:00+09:00", null);

		change("{ \"openRangeDays\": " + days + " }").andExpect(status().isOk());

		assertThat(slotDays()).containsExactly("2026-12-03");
	}

	@Test
	void 회차_생성이_실패하면_PATCH가_실패하고_오픈_범위는_그대로() throws Exception {
		given.recurrence(instructorId, THURSDAY, "20:00", 8, true);
		jdbc.sql("ALTER TABLE class_slot ADD CONSTRAINT " + BLOCK_INSERT + " CHECK (false) NOT VALID").update();
		try {
			change("{ \"openRangeDays\": 28 }").andExpect(status().isInternalServerError());
		}
		finally {
			dropBlockInsert();
		}

		assertThat(openRangeDays()).isEqualTo(14);
		assertThat(slotDays()).isEmpty();
	}

	@Test
	void 다른_강사의_오픈_범위와_회차는_바뀌지_않는다() throws Exception {
		long other = given.instructor("2002");
		given.recurrence(other, THURSDAY, "20:00", 8, true);

		change("{ \"openRangeDays\": 28 }").andExpect(status().isOk());

		assertThat(jdbc.sql("SELECT open_range_days FROM setting WHERE instructor_id = :id")
			.param("id", other)
			.query(Integer.class)
			.single()).isEqualTo(14);
		assertThat(slotDays()).isEmpty();
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		mockMvc.perform(get("/instructor/settings")).andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/instructor/settings").header("X-CSRF", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"openRangeDays\": 21 }")).andExpect(status().isUnauthorized());
	}

	private ResultActions change(String body) throws Exception {
		return mockMvc.perform(patch("/instructor/settings").header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private int openRangeDays() {
		return jdbc.sql("SELECT open_range_days FROM setting WHERE instructor_id = :id")
			.param("id", instructorId)
			.query(Integer.class)
			.single();
	}

	private List<String> slotDays() {
		return jdbc.sql("SELECT occurs_on::text FROM class_slot ORDER BY occurs_on").query(String.class).list();
	}

	private void dropBlockInsert() {
		jdbc.sql("ALTER TABLE class_slot DROP CONSTRAINT IF EXISTS " + BLOCK_INSERT).update();
	}

}
