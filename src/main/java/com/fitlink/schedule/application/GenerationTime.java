package com.fitlink.schedule.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// 회차를 만드는 실행 하나의 기준 시각. 오늘과 지금을 시계에서 한 번에 읽어 자정 경계에서 둘이 어긋나지 않게 한다
public record GenerationTime(LocalDate today, OffsetDateTime now) {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	public static GenerationTime at(Clock clock) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		return new GenerationTime(now.atZoneSameInstant(SEOUL).toLocalDate(), now);
	}

}
