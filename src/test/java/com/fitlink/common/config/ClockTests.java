package com.fitlink.common.config;

import com.fitlink.IntegrationTest;
import com.fitlink.TestClock;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 컨텍스트에 주입되는 시계가 테스트 시계이고, 테스트가 바꾼 "지금"을 모든 사용처가 따라가는지 본다
class ClockTests extends IntegrationTest {

	@Autowired
	Clock clock;

	@Test
	void 테스트_시계는_목요일_12시로_고정돼_있고_테스트가_바꿀_수_있다() {
		assertThat(LocalDate.now(clock).getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
		assertThat(clock.instant()).isEqualTo(TestClock.DEFAULT);

		testClock.set("2026-11-17T21:00:00+09:00");

		assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 11, 17));
	}

	@Test
	void zone을_바꿔_받아_둔_시계도_나중에_바꾼_시각을_따라간다() {
		Clock utc = clock.withZone(ZoneOffset.UTC);

		testClock.set("2026-11-17T21:00:00+09:00");

		assertThat(utc.instant()).isEqualTo(clock.instant());
	}

}
