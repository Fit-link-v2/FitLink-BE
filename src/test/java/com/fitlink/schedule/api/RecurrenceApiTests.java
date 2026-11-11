package com.fitlink.schedule.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.infra.SessionRepository;
import com.fitlink.schedule.ScheduleFixtures;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울), 오픈 범위는 14일이다
class RecurrenceApiTests extends IntegrationTest {

	static final int SUNDAY = 0;

	static final int MONDAY = 1;

	static final int TUESDAY = 2;

	static final int WEDNESDAY = 3;

	static final int SATURDAY = 6;

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
	void 선택한_요일마다_규칙을_하나씩_만들고_201로_돌려준다() throws Exception {
		register("""
				{ "weekdays": ["MON", "WED"], "startTime": "20:00", "capacity": 8 }""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items[*].weekday", contains("MON", "WED")))
			.andExpect(jsonPath("$.items[*].startTime", contains("20:00", "20:00")))
			.andExpect(jsonPath("$.items[*].capacity", contains(8, 8)))
			.andExpect(jsonPath("$.items[*].active", contains(true, true)))
			.andExpect(jsonPath("$.items[0].id").isNumber());

		assertThat(count("recurrence")).isEqualTo(2);
	}

	@Test
	void 등록_응답도_목록처럼_월요일부터_순서대로_준다() throws Exception {
		register("""
				{ "weekdays": ["SUN", "WED", "MON"], "startTime": "20:00", "capacity": 8 }""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items[*].weekday", contains("MON", "WED", "SUN")));
	}

	@Test
	void 등록_응답_뒤_오픈_범위_안의_회차가_있다() throws Exception {
		register("""
				{ "weekdays": ["MON", "WED"], "startTime": "20:00", "capacity": 8 }""").andExpect(status().isCreated());

		assertThat(jdbc.sql("SELECT occurs_on FROM class_slot ORDER BY occurs_on").query(LocalDate.class).list())
			.containsExactly(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 23),
					LocalDate.of(2026, 11, 25));
	}

	@Test
	void 목록은_월요일부터_일요일까지_같은_요일은_시각_순이고_다른_강사의_규칙은_없다() throws Exception {
		given.recurrence(instructorId, SUNDAY, "10:00", 8, true);
		given.recurrence(instructorId, MONDAY, "20:00", 8, true);
		given.recurrence(instructorId, SATURDAY, "09:00", 8, false);
		given.recurrence(instructorId, MONDAY, "07:00", 8, true);
		given.recurrence(given.instructor("2002"), TUESDAY, "20:00", 8, true);

		mockMvc.perform(get("/instructor/recurrences").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].weekday", contains("MON", "MON", "SAT", "SUN")))
			.andExpect(jsonPath("$.items[*].startTime", contains("07:00", "20:00", "09:00", "10:00")))
			.andExpect(jsonPath("$.items[*].active", contains(true, true, false, true)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("잘못된_입력")
	void 입력이_잘못되면_400_VALIDATION_FAILED이고_아무것도_저장하지_않는다(String why, String body, String field)
			throws Exception {
		register(body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value(field))
			.andExpect(jsonPath("$.fields[0].reason").isString());

		assertThat(count("recurrence")).isZero();
		assertThat(count("class_slot")).isZero();
	}

	static Stream<Arguments> 잘못된_입력() {
		return Stream.of(
				Arguments.of("요일 없음", """
						{ "weekdays": [], "startTime": "20:00", "capacity": 8 }""", "weekdays"),
				Arguments.of("요일 중복", """
						{ "weekdays": ["MON", "MON"], "startTime": "20:00", "capacity": 8 }""", "weekdays"),
				Arguments.of("빈 요일 값", """
						{ "weekdays": ["MON", null], "startTime": "20:00", "capacity": 8 }""", "weekdays"),
				Arguments.of("모르는 요일", """
						{ "weekdays": ["XYZ"], "startTime": "20:00", "capacity": 8 }""", "weekdays"),
				Arguments.of("시각 형식 HH:mm 아님", """
						{ "weekdays": ["MON"], "startTime": "8:00", "capacity": 8 }""", "startTime"),
				Arguments.of("초까지 적은 시각", """
						{ "weekdays": ["MON"], "startTime": "20:00:00", "capacity": 8 }""", "startTime"),
				Arguments.of("없는 시각", """
						{ "weekdays": ["MON"], "startTime": "25:00", "capacity": 8 }""", "startTime"),
				Arguments.of("시각 없음", """
						{ "weekdays": ["MON"], "capacity": 8 }""", "startTime"),
				Arguments.of("정원 0", """
						{ "weekdays": ["MON"], "startTime": "20:00", "capacity": 0 }""", "capacity"),
				Arguments.of("정원 51", """
						{ "weekdays": ["MON"], "startTime": "20:00", "capacity": 51 }""", "capacity"),
				Arguments.of("정원 소수", """
						{ "weekdays": ["MON"], "startTime": "20:00", "capacity": 8.7 }""", "capacity"),
				Arguments.of("정원 없음", """
						{ "weekdays": ["MON"], "startTime": "20:00" }""", "capacity"));
	}

	@Test
	void 같은_요일_시각의_켜진_규칙이_있으면_409_겹친_요일만_알려주고_나머지_요일도_저장하지_않는다() throws Exception {
		given.recurrence(instructorId, WEDNESDAY, "20:00", 8, true);

		register("""
				{ "weekdays": ["MON", "WED"], "startTime": "20:00", "capacity": 8 }""")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RECURRENCE_DUPLICATE"))
			.andExpect(jsonPath("$.weekdays", contains("WED")));

		assertThat(count("recurrence")).isEqualTo(1);
		assertThat(count("class_slot")).isZero();
	}

	@Test
	void 꺼진_같은_요일_시각_규칙과는_겹치지_않고_남은_회차를_이어받는다() throws Exception {
		long off = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, false);
		long moved = given.slot(instructorId, off, "2026-11-18", "2026-11-18T19:30:00+09:00", null);

		register("""
				{ "weekdays": ["WED"], "startTime": "20:00", "capacity": 10 }""").andExpect(status().isCreated());

		long created = jdbc.sql("SELECT id FROM recurrence WHERE active").query(Long.class).single();
		assertThat(jdbc.sql("SELECT recurrence_id FROM class_slot WHERE id = :id").param("id", moved).query(Long.class)
			.single()).isEqualTo(created);
		assertThat(jdbc.sql("SELECT count(*) FROM class_slot WHERE occurs_on = '2026-11-18'").query(Integer.class)
			.single()).isEqualTo(1);
	}

	@Test
	void 다른_강사의_같은_요일_시각_규칙과는_겹치지_않는다() throws Exception {
		given.recurrence(given.instructor("2002"), WEDNESDAY, "20:00", 8, true);

		register("""
				{ "weekdays": ["WED"], "startTime": "20:00", "capacity": 8 }""").andExpect(status().isCreated());
	}

	private ResultActions register(String body) throws Exception {
		return mockMvc.perform(post("/instructor/recurrences").header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private int count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
	}

}
