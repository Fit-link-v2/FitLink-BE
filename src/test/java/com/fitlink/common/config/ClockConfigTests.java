package com.fitlink.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClockConfigTests {

	@Test
	void 애플리케이션_시계는_서울_기준이다() {
		assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
	}

}
