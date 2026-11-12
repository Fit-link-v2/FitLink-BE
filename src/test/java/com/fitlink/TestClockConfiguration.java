package com.fitlink;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	// 애플리케이션의 Clock 대신 쓴다. 테스트마다 IntegrationTest가 기본 시각으로 되돌린다
	@Bean
	@Primary
	TestClock testClock() {
		return new TestClock();
	}

}
