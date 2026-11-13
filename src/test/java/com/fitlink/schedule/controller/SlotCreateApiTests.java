package com.fitlink.schedule.controller;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.schedule.ScheduleFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울), 오픈 범위는 기본값 14일이라 2026-11-12 ~ 2026-11-25다
class SlotCreateApiTests extends IntegrationTest {

	static final int THURSDAY = 4;

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
	void 일회성_수업을_열면_201이고_규칙_없는_슬롯_하나가_생긴다() throws Exception {
		create("2026-11-20", "20:00", 6).andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.startsAt").value("2026-11-20T20:00:00+09:00"))
			.andExpect(jsonPath("$.capacity").value(6))
			.andExpect(jsonPath("$.taken").value(0))
			.andExpect(jsonPath("$.canceledAt").value(nullValue()))
			.andExpect(jsonPath("$.recurrence").value(nullValue()));

		Map<String, Object> row = jdbc.sql("SELECT recurrence_id, occurs_on, instructor_id FROM class_slot")
			.query()
			.singleRow();
		assertThat(row.get("recurrence_id")).isNull();
		assertThat(row.get("occurs_on").toString()).isEqualTo("2026-11-20");
		assertThat(row.get("instructor_id")).isEqualTo(instructorId);
	}

	@Test
	void 일회성_수업을_열어도_회차_생성은_돌지_않는다() throws Exception {
		given.recurrence(instructorId, THURSDAY, "09:00", 8, true);

		create("2026-11-20", "20:00", 6).andExpect(status().isCreated());

		assertThat(slotCount()).isEqualTo(1);
	}

	@Test
	void 오늘_지금_이후와_범위_마지막_날은_열_수_있다() throws Exception {
		create("2026-11-12", "12:01", 6).andExpect(status().isCreated());
		create("2026-11-25", "23:59", 6).andExpect(status().isCreated());
	}

	@ParameterizedTest(name = "{0} {1} → 400 {2}")
	@CsvSource({
			"2026-11-12, 12:00, startTime",   // 지금
			"2026-11-12, 11:59, startTime",   // 지금 이전
			"2026-11-11, 20:00, date",        // 어제
			"2026-11-26, 20:00, date",        // 범위 끝 날짜는 넣지 않는다
	})
	void 지금_이전이거나_오픈_범위_밖이면_400과_필드이고_행이_없다(String date, String startTime, String field)
			throws Exception {
		create(date, startTime, 6).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value(field));

		assertThat(slotCount()).isZero();
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
			"{ \"date\": \"2026-11-20\", \"startTime\": \"20:00\", \"capacity\": 0 }   | capacity",
			"{ \"date\": \"2026-11-20\", \"startTime\": \"20:00\", \"capacity\": 51 }  | capacity",
			"{ \"date\": \"2026-11-20\", \"startTime\": \"20:00\" }                    | capacity",
			"{ \"startTime\": \"20:00\", \"capacity\": 6 }                             | date",
			"{ \"date\": \"11/20\", \"startTime\": \"20:00\", \"capacity\": 6 }        | date",
			"{ \"date\": \"2026-11-20\", \"capacity\": 6 }                             | startTime",
			"{ \"date\": \"2026-11-20\", \"startTime\": \"8:00\", \"capacity\": 6 }    | startTime",
	})
	void 정원이_1_50_밖이거나_값이_없거나_형식이_틀리면_400과_필드(String body, String field) throws Exception {
		send(body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value(field));

		assertThat(slotCount()).isZero();
	}

	@Test
	void 같은_시각에_반복_회차가_있으면_409_SLOT_TIME_TAKEN이고_행이_없다() throws Exception {
		long rule = given.recurrence(instructorId, THURSDAY, "20:00", 8, true);
		given.slot(instructorId, rule, "2026-11-19", "2026-11-19T20:00:00+09:00", null);

		create("2026-11-19", "20:00", 6).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SLOT_TIME_TAKEN"));

		assertThat(slotCount()).isEqualTo(1);
	}

	@Test
	void 휴강한_수업의_시각이나_다른_강사의_같은_시각에는_열_수_있다() throws Exception {
		given.slot(instructorId, null, "2026-11-19", "2026-11-19T20:00:00+09:00", "2026-11-12T10:00:00+09:00");
		given.slot(given.instructor("2002"), null, "2026-11-19", "2026-11-19T21:00:00+09:00", null);

		create("2026-11-19", "20:00", 6).andExpect(status().isCreated());
		create("2026-11-19", "21:00", 6).andExpect(status().isCreated());
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		mockMvc.perform(post("/instructor/slots")
			.header("X-CSRF", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"date\": \"2026-11-20\", \"startTime\": \"20:00\", \"capacity\": 6 }"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions create(String date, String startTime, int capacity) throws Exception {
		return send("{ \"date\": \"" + date + "\", \"startTime\": \"" + startTime + "\", \"capacity\": " + capacity
				+ " }");
	}

	private ResultActions send(String body) throws Exception {
		return mockMvc.perform(post("/instructor/slots")
			.header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private long slotCount() {
		return jdbc.sql("SELECT count(*) FROM class_slot").query(Long.class).single();
	}

}
