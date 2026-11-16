package com.fitlink.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitlink.IntegrationTest;
import com.fitlink.instructor.InstructorOpenRangeExtendedEvent;
import com.fitlink.schedule.ScheduleFixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class OpenRangeListenerTests extends IntegrationTest {

	static final int THURSDAY = 4;

	@Autowired
	ApplicationEventPublisher events;

	// 시계는 11-12 12:00이지만 이벤트는 11-19 20:00을 싣는다. 시계를 다시 읽으면 11-12 · 11-19 회차가 생긴다
	@Test
	void 오픈_범위가_늘었다는_이벤트를_받으면_이벤트의_오늘_지금_기준으로_회차를_만든다() {
		ScheduleFixtures given = new ScheduleFixtures(jdbc);
		long instructorId = given.instructor("1001");
		given.recurrence(instructorId, THURSDAY, "20:00", 8, true);

		events.publishEvent(new InstructorOpenRangeExtendedEvent(instructorId, LocalDate.parse("2026-11-19"),
				OffsetDateTime.parse("2026-11-19T20:00:00+09:00")));

		assertThat(jdbc.sql("SELECT occurs_on::text FROM class_slot ORDER BY occurs_on").query(String.class).list())
			.containsExactly("2026-11-26");
	}

}
