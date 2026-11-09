package com.fitlink.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GenerationTimeTests {

	@Test
	void 오늘은_받은_시계의_zone과_상관없이_서울_날짜다() {
		// 서울은 11-12 00:30, UTC는 아직 11-11 15:30
		Clock utc = Clock.fixed(Instant.parse("2026-11-11T15:30:00Z"), ZoneOffset.UTC);

		assertThat(GenerationTime.at(utc).today()).isEqualTo(LocalDate.of(2026, 11, 12));
	}

}
