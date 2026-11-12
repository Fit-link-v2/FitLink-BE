package com.fitlink.schedule.application;

import java.time.ZoneId;

// 수업 날짜와 시각은 서울 기준이다
public final class ScheduleZone {

	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private ScheduleZone() {
	}

}
