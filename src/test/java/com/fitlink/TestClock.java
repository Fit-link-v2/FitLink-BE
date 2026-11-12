package com.fitlink;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

// 테스트가 "지금"을 정한다. 시간표는 오늘 지난 회차 · 오픈 범위 끝 날처럼 시각 경계가 결과를 바꾸기 때문이다
public final class TestClock extends Clock {

	// 목요일 12:00(서울). 같은 주 화요일은 지났고 다음 주 화요일은 오픈 범위 안이다
	public static final Instant DEFAULT = OffsetDateTime.parse("2026-11-12T12:00:00+09:00").toInstant();

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private volatile Instant now = DEFAULT;

	public void set(String offsetDateTime) {
		now = OffsetDateTime.parse(offsetDateTime).toInstant();
	}

	public void reset() {
		now = DEFAULT;
	}

	@Override
	public ZoneId getZone() {
		return SEOUL;
	}

	// 다른 zone으로 바꾼 시계도 같은 "지금"을 본다. 고정 사본을 주면 빈이 만들 때 한 번 받아 둔 시계가 set()을 따라오지 않는다
	@Override
	public Clock withZone(ZoneId zone) {
		TestClock source = this;
		return new Clock() {

			@Override
			public ZoneId getZone() {
				return zone;
			}

			@Override
			public Clock withZone(ZoneId other) {
				return source.withZone(other);
			}

			@Override
			public Instant instant() {
				return source.instant();
			}

		};
	}

	@Override
	public Instant instant() {
		return now;
	}

}
