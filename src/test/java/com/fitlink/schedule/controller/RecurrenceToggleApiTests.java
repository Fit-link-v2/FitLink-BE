package com.fitlink.schedule.controller;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.schedule.ScheduleFixtures;
import com.fitlink.schedule.model.GenerationTime;
import com.fitlink.schedule.service.SlotGeneration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울), 오픈 범위는 14일이라 수요일 회차는 11-18 · 11-25다.
// 시계를 11-19 0시로 옮기면 범위 끝에 12-02 수요일이 새로 들어온다
class RecurrenceToggleApiTests extends IntegrationTest {

	static final int WEDNESDAY = 3;

	static final String NEXT_WEEK = "2026-11-19T00:00:00+09:00";

	@Autowired
	SessionRepository sessions;

	@Autowired
	SlotGeneration generation;

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
	void 끄면_이미_만든_회차는_그대로이고_범위가_늘어도_그_규칙의_회차는_더_생기지_않는다() throws Exception {
		long rule = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, true);
		generateAll();

		toggle(rule, false).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(rule))
			.andExpect(jsonPath("$.weekday").value("WED"))
			.andExpect(jsonPath("$.startTime").value("20:00"))
			.andExpect(jsonPath("$.active").value(false));

		testClock.set(NEXT_WEEK);
		generateAll();

		assertThat(slotDays()).containsExactly(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 25));
		assertThat(active(rule)).isFalse();
	}

	@Test
	void 다시_켜면_응답_전에_오픈_범위_안의_회차가_생긴다() throws Exception {
		long rule = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, false);

		toggle(rule, true).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));

		assertThat(slotDays()).containsExactly(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 25));
	}

	@Test
	void 같은_요일_시각의_켜진_규칙이_있으면_다시_켜기는_409이고_꺼진_채로_둔다() throws Exception {
		long off = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, false);
		given.recurrence(instructorId, WEDNESDAY, "20:00", 10, true);

		toggle(off, true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RECURRENCE_DUPLICATE"))
			.andExpect(jsonPath("$.weekdays", contains("WED")));

		assertThat(active(off)).isFalse();
		assertThat(count("class_slot")).isZero();
	}

	@Test
	void 이미_켜진_규칙을_켜거나_꺼진_규칙을_꺼도_200이고_그대로다() throws Exception {
		long on = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, true);
		long off = given.recurrence(instructorId, WEDNESDAY, "07:00", 8, false);

		toggle(on, true).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
		toggle(off, false).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

		assertThat(active(on)).isTrue();
		assertThat(active(off)).isFalse();
	}

	@Test
	void A를_끄고_B를_등록하면_B가_이어받고_B를_끄고_A를_켜면_A가_다시_이어받는다() throws Exception {
		long a = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, true);
		generateAll();
		long moved = slotOn("2026-11-18");
		jdbc.sql("UPDATE class_slot SET starts_at = '2026-11-18T19:30:00+09:00' WHERE id = :id")
			.param("id", moved)
			.update();

		toggle(a, false).andExpect(status().isOk());
		long b = register("""
				{ "weekdays": ["WED"], "startTime": "20:00", "capacity": 10 }""");

		assertThat(owners()).containsOnly(b);
		assertThat(slotDays()).containsExactly(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 25));
		assertThat(startsAt(moved)).isAtSameInstantAs(OffsetDateTime.parse("2026-11-18T19:30:00+09:00"));

		toggle(b, false).andExpect(status().isOk());
		toggle(a, true).andExpect(status().isOk());

		assertThat(owners()).containsOnly(a);
		assertThat(slotDays()).containsExactly(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 25));
		assertThat(startsAt(moved)).isAtSameInstantAs(OffsetDateTime.parse("2026-11-18T19:30:00+09:00"));
	}

	@Test
	void 이어받은_회차는_원래_정원이고_늘어난_범위의_새_회차는_새_규칙의_정원이다() throws Exception {
		long a = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, true);
		generateAll();
		toggle(a, false).andExpect(status().isOk());
		register("""
				{ "weekdays": ["WED"], "startTime": "20:00", "capacity": 10 }""");

		testClock.set(NEXT_WEEK);
		generateAll();

		assertThat(jdbc.sql("SELECT capacity FROM class_slot ORDER BY occurs_on").query(Integer.class).list())
			.containsExactly(8, 8, 10);
	}

	@Test
	void 다른_강사의_규칙이나_없는_id는_404이고_바뀌지_않는다() throws Exception {
		long others = given.recurrence(given.instructor("2002"), WEDNESDAY, "20:00", 8, true);

		toggle(others, false).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		toggle(others + 100, false).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));

		assertThat(active(others)).isTrue();
	}

	@Test
	void active가_없으면_400_VALIDATION_FAILED() throws Exception {
		long rule = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, true);

		patchBody(rule, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value("active"));

		assertThat(active(rule)).isTrue();
	}

	@Test
	void 경로의_id가_숫자가_아니면_400_VALIDATION_FAILED() throws Exception {
		mockMvc
			.perform(patch("/instructor/recurrences/abc").header("X-CSRF", "1")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"active\": false }"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value("id"));
	}

	private ResultActions toggle(long id, boolean active) throws Exception {
		return patchBody(id, "{ \"active\": " + active + " }");
	}

	private ResultActions patchBody(long id, String body) throws Exception {
		return mockMvc.perform(patch("/instructor/recurrences/{id}", id).header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private long register(String body) throws Exception {
		mockMvc
			.perform(post("/instructor/recurrences").header("X-CSRF", "1")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());
		return jdbc.sql("SELECT max(id) FROM recurrence").query(Long.class).single();
	}

	private void generateAll() {
		generation.generateAll(GenerationTime.at(testClock));
	}

	private boolean active(long rule) {
		return jdbc.sql("SELECT active FROM recurrence WHERE id = :id").param("id", rule).query(Boolean.class).single();
	}

	private List<LocalDate> slotDays() {
		return jdbc.sql("SELECT occurs_on FROM class_slot ORDER BY occurs_on").query(LocalDate.class).list();
	}

	private List<Long> owners() {
		return jdbc.sql("SELECT recurrence_id FROM class_slot").query(Long.class).list();
	}

	private long slotOn(String day) {
		return jdbc.sql("SELECT id FROM class_slot WHERE occurs_on = CAST(:day AS date)")
			.param("day", day)
			.query(Long.class)
			.single();
	}

	private OffsetDateTime startsAt(long slot) {
		return jdbc.sql("SELECT starts_at FROM class_slot WHERE id = :id")
			.param("id", slot)
			.query(OffsetDateTime.class)
			.single();
	}

	private int count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
	}

}
