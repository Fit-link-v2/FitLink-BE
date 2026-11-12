package com.fitlink.schedule.controller;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.schedule.ScheduleFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울)이다
class SlotCancelApiTests extends IntegrationTest {

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
	void 휴강하면_슬롯을_남기고_지금_시각을_canceledAt에_채운다() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);

		cancel(slot).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(slot))
			.andExpect(jsonPath("$.canceledAt").value("2026-11-12T12:00:00+09:00"));

		assertThat(canceledAt(slot)).isAtSameInstantAs(OffsetDateTime.parse("2026-11-12T12:00:00+09:00"));
	}

	@Test
	void 예약이_있어도_휴강된다() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);
		jdbc.sql("UPDATE class_slot SET taken = 3 WHERE id = :id").param("id", slot).update();

		cancel(slot).andExpect(status().isOk()).andExpect(jsonPath("$.taken").value(3));

		assertThat(canceledAt(slot)).isNotNull();
	}

	@Test
	void 이미_휴강한_슬롯은_시작했어도_200이고_처음_휴강_시각을_그대로_둔다() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-12", "2026-11-12T11:00:00+09:00",
				"2026-11-11T10:00:00+09:00");

		cancel(slot).andExpect(status().isOk())
			.andExpect(jsonPath("$.canceledAt").value("2026-11-11T10:00:00+09:00"));

		assertThat(canceledAt(slot)).isAtSameInstantAs(OffsetDateTime.parse("2026-11-11T10:00:00+09:00"));
	}

	@Test
	void 지금_시작한_수업은_422_CLASS_STARTED이고_휴강하지_않는다() throws Exception {
		long slot = given.slot(instructorId, null, "2026-11-12", "2026-11-12T12:00:00+09:00", null);

		cancel(slot).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CLASS_STARTED"));

		assertThat(canceledAt(slot)).isNull();
	}

	@Test
	void 다른_강사의_슬롯이나_없는_id는_404이고_휴강하지_않는다() throws Exception {
		long others = given.slot(given.instructor("2002"), null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);

		cancel(others).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		cancel(others + 100).andExpect(status().isNotFound());

		assertThat(canceledAt(others)).isNull();
	}

	private ResultActions cancel(long id) throws Exception {
		return mockMvc.perform(post("/instructor/slots/{id}/cancel", id).header("X-CSRF", "1").cookie(session));
	}

	private OffsetDateTime canceledAt(long slot) {
		return jdbc.sql("SELECT canceled_at FROM class_slot WHERE id = :id")
			.param("id", slot)
			.query(OffsetDateTime.class)
			.list()
			.getFirst();
	}

}
