package com.fitlink.schedule.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

// 강사가 보는 슬롯 하나. 휴강이면 canceledAt이 있고, 일회성 수업이면 recurrence가 null이다.
// occursOn은 수업 날짜(서울)다. 시각을 바꿔도 이 날 안에서 바뀐다
public record Slot(long id, LocalDate occursOn, OffsetDateTime startsAt, int capacity, int taken,
		OffsetDateTime canceledAt, Rule recurrence) {

	public boolean canceled() {
		return canceledAt != null;
	}

	// 시작 시각이 지금이면 이미 시작한 수업이다. 목록의 "지금 이후"와 같은 경계다
	public boolean startedAt(OffsetDateTime now) {
		return !startsAt.isAfter(now);
	}

	// 슬롯을 만든 규칙. startTime은 규칙의 시각이라, 이 회차만 옮겼으면 슬롯의 시각과 다르다
	public record Rule(long id, Weekday weekday, LocalTime startTime) {
	}

}
