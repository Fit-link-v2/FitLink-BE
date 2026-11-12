package com.fitlink.schedule.repository;

import com.fitlink.IntegrationTest;
import com.fitlink.schedule.ScheduleFixtures;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 읽고 판정한 뒤 다른 요청이 끼어들어도 조건부 UPDATE가 스스로 막는지 본다. 서비스의 판정을 거치지 않고 바로 부른다
class SlotRepositoryTests extends IntegrationTest {

	static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-12T12:00:00+09:00");

	static final OffsetDateTime LATER = OffsetDateTime.parse("2026-11-13T10:00:00+09:00");

	@Autowired
	SlotRepository slots;

	ScheduleFixtures given;

	long instructorId;

	@BeforeEach
	void 강사() {
		given = new ScheduleFixtures(jdbc);
		instructorId = given.instructor("1001");
	}

	@Test
	void 시각_변경은_휴강했거나_예약이_있거나_시작한_슬롯과_다른_강사의_슬롯을_바꾸지_않는다() {
		long canceled = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00",
				"2026-11-12T10:00:00+09:00");
		long booked = given.slot(instructorId, null, "2026-11-13", "2026-11-13T08:00:00+09:00", null);
		jdbc.sql("UPDATE class_slot SET taken = 1 WHERE id = :id").param("id", booked).update();
		long started = given.slot(instructorId, null, "2026-11-12", "2026-11-12T12:00:00+09:00", null);
		long others = given.slot(given.instructor("2002"), null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);
		long open = given.slot(instructorId, null, "2026-11-13", "2026-11-13T07:00:00+09:00", null);

		assertThat(slots.changeTime(instructorId, canceled, LATER, NOW)).isFalse();
		assertThat(slots.changeTime(instructorId, booked, LATER, NOW)).isFalse();
		assertThat(slots.changeTime(instructorId, started, LATER, NOW)).isFalse();
		assertThat(slots.changeTime(instructorId, others, LATER, NOW)).isFalse();
		assertThat(slots.changeTime(instructorId, open, LATER, NOW)).isTrue();
	}

	@Test
	void 휴강은_이미_휴강했거나_시작한_슬롯과_다른_강사의_슬롯을_바꾸지_않고_예약이_있어도_휴강한다() {
		long canceled = given.slot(instructorId, null, "2026-11-13", "2026-11-13T09:00:00+09:00",
				"2026-11-12T10:00:00+09:00");
		long started = given.slot(instructorId, null, "2026-11-12", "2026-11-12T12:00:00+09:00", null);
		long others = given.slot(given.instructor("2002"), null, "2026-11-13", "2026-11-13T09:00:00+09:00", null);
		long booked = given.slot(instructorId, null, "2026-11-13", "2026-11-13T08:00:00+09:00", null);
		jdbc.sql("UPDATE class_slot SET taken = 1 WHERE id = :id").param("id", booked).update();

		assertThat(slots.cancel(instructorId, canceled, NOW)).isFalse();
		assertThat(slots.cancel(instructorId, started, NOW)).isFalse();
		assertThat(slots.cancel(instructorId, others, NOW)).isFalse();
		assertThat(slots.cancel(instructorId, booked, NOW)).isTrue();
	}

}
