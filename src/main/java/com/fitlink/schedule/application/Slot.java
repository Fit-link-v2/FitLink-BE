package com.fitlink.schedule.application;

import java.time.LocalTime;
import java.time.OffsetDateTime;

// 강사가 보는 슬롯 하나. 휴강이면 canceledAt이 있고, 일회성 수업이면 recurrence가 null이다
public record Slot(long id, OffsetDateTime startsAt, int capacity, int taken, OffsetDateTime canceledAt,
		Rule recurrence) {

	boolean canceled() {
		return canceledAt != null;
	}

	// 시작 시각이 지금이면 이미 시작한 수업이다. 목록의 "지금 이후"와 같은 경계다
	boolean startedAt(OffsetDateTime now) {
		return !startsAt.isAfter(now);
	}

	// 슬롯을 만든 규칙. startTime은 규칙의 시각이라, 이 회차만 옮겼으면 슬롯의 시각과 다르다
	public record Rule(long id, Weekday weekday, LocalTime startTime) {
	}

}
