package com.fitlink.schedule.controller;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.schedule.ScheduleFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울)이다
class SlotChangeApiTests extends IntegrationTest {

	static final int WEDNESDAY = 3;

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
	void 반복_회차의_시각을_바꾸면_그_슬롯만_같은_날_안에서_바뀌고_규칙과_다른_주_회차는_그대로다() throws Exception {
		long rule = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, true);
		long thisWeek = given.slot(instructorId, rule, "2026-11-18", "2026-11-18T20:00:00+09:00", null);
		long nextWeek = given.slot(instructorId, rule, "2026-11-25", "2026-11-25T20:00:00+09:00", null);

		change(thisWeek, "{ \"startTime\": \"19:30\" }").andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(thisWeek))
			.andExpect(jsonPath("$.startsAt").value("2026-11-18T19:30:00+09:00"))
			.andExpect(jsonPath("$.recurrence.startTime").value("20:00"));

		assertThat(occursOn(thisWeek)).isEqualTo(LocalDate.of(2026, 11, 18));
		assertThat(startsAt(nextWeek)).isAtSameInstantAs(OffsetDateTime.parse("2026-11-25T20:00:00+09:00"));
		assertThat(jdbc.sql("SELECT start_time::text FROM recurrence WHERE id = :id")
			.param("id", rule)
			.query(String.class)
			.single()).isEqualTo("20:00:00");
	}

	@Test
	void 일회성_수업도_같은_날_안에서_시각을_바꾼다() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);

		change(slot, "{ \"startTime\": \"10:00\" }").andExpect(status().isOk())
			.andExpect(jsonPath("$.startsAt").value("2026-11-13T10:00:00+09:00"))
			.andExpect(jsonPath("$.recurrence").value(nullValue()));
	}

	@Test
	void 휴강한_슬롯은_시작했어도_422_SLOT_CANCELED() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-12", "2026-11-12T11:00:00+09:00",
				"2026-11-11T10:00:00+09:00");

		change(slot, "{ \"startTime\": \"20:00\" }").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("SLOT_CANCELED"));
	}

	@Test
	void 지금_시작한_슬롯은_예약이_있고_새_시각이_지났어도_422_CLASS_STARTED() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-12", "2026-11-12T12:00:00+09:00", null);
		book(slot);

		change(slot, "{ \"startTime\": \"09:00\" }").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("CLASS_STARTED"));

		assertThat(startsAt(slot)).isAtSameInstantAs(OffsetDateTime.parse("2026-11-12T12:00:00+09:00"));
	}

	@Test
	void 새_시각이_지금이거나_지났으면_예약이_있어도_400_startTime() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-12", "2026-11-12T20:00:00+09:00", null);
		book(slot);

		change(slot, "{ \"startTime\": \"12:00\" }").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value("startTime"));

		assertThat(startsAt(slot)).isAtSameInstantAs(OffsetDateTime.parse("2026-11-12T20:00:00+09:00"));
	}

	@Test
	void 반복_회차에_date를_보내면_예약이_있어도_400_date() throws Exception {
		long rule = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, true);
		long slot = given.slot(instructorId, rule, "2026-11-18", "2026-11-18T20:00:00+09:00", null);
		book(slot);

		change(slot, "{ \"startTime\": \"19:30\", \"date\": \"2026-11-19\" }").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields[0].name").value("date"));

		assertThat(occursOn(slot)).isEqualTo(LocalDate.of(2026, 11, 18));
	}

	@Test
	void 예약이_있으면_같은_시각_수업이_있어도_422_SLOT_HAS_BOOKINGS() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);
		given.slot(instructorId, null, "2026-11-13", "2026-11-13T10:00:00+09:00", null);
		book(slot);

		change(slot, "{ \"startTime\": \"10:00\" }").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("SLOT_HAS_BOOKINGS"));

		assertThat(startsAt(slot)).isAtSameInstantAs(OffsetDateTime.parse("2026-11-13T09:00:00+09:00"));
	}

	@Test
	void 같은_시각에_휴강_아닌_수업이_있으면_409_SLOT_TIME_TAKEN이고_바뀌지_않는다() throws Exception {
		long rule = given.recurrence(instructorId, THURSDAY, "09:00", 8, true);
		long slot = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);
		given.slot(instructorId, rule, "2026-11-13", "2026-11-13T10:00:00+09:00", null);

		change(slot, "{ \"startTime\": \"10:00\" }").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SLOT_TIME_TAKEN"));

		assertThat(startsAt(slot)).isAtSameInstantAs(OffsetDateTime.parse("2026-11-13T09:00:00+09:00"));
	}

	@Test
	void 휴강한_수업의_시각이나_다른_강사의_같은_시각으로는_옮길_수_있다() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);
		given.slot(instructorId, null, "2026-11-13", "2026-11-13T10:00:00+09:00", "2026-11-12T10:00:00+09:00");
		given.slot(given.instructor("2002"), null, "2026-11-13", "2026-11-13T11:00:00+09:00", null);

		change(slot, "{ \"startTime\": \"10:00\" }").andExpect(status().isOk());
		change(slot, "{ \"startTime\": \"11:00\" }").andExpect(status().isOk())
			.andExpect(jsonPath("$.startsAt").value("2026-11-13T11:00:00+09:00"));
	}

	@Test
	void 다른_강사의_슬롯이나_없는_id는_404이고_바뀌지_않는다() throws Exception {
		long others = given.slot(given.instructor("2002"), null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);

		change(others, "{ \"startTime\": \"10:00\" }").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		change(others + 100, "{ \"startTime\": \"10:00\" }").andExpect(status().isNotFound());

		assertThat(startsAt(others)).isAtSameInstantAs(OffsetDateTime.parse("2026-11-13T09:00:00+09:00"));
	}

	@Test
	void startTime이_없거나_HH_mm이_아니면_400_startTime() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);

		change(slot, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0].name").value("startTime"));
		change(slot, "{ \"startTime\": \"9:00\" }").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields[0].name").value("startTime"));
	}

	private ResultActions change(long id, String body) throws Exception {
		return mockMvc.perform(patch("/instructor/slots/{id}", id).header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private void book(long slot) {
		jdbc.sql("UPDATE class_slot SET taken = 1 WHERE id = :id").param("id", slot).update();
	}

	private OffsetDateTime startsAt(long slot) {
		return jdbc.sql("SELECT starts_at FROM class_slot WHERE id = :id")
			.param("id", slot)
			.query(OffsetDateTime.class)
			.single();
	}

	private LocalDate occursOn(long slot) {
		return jdbc.sql("SELECT occurs_on FROM class_slot WHERE id = :id")
			.param("id", slot)
			.query(LocalDate.class)
			.single();
	}

}
