package com.fitlink.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 애플리케이션이 "오늘" · "지금"을 구할 때는 이 Clock만 쓴다. 날짜 경계는 서울 기준이고, 테스트는 고정한 시계로 바꿔 끼운다.
// 시간표 SQL은 이 값을 파라미터로 받고 now()를 쓰지 않는다. 세션 만료만 DB의 now()로 계산한다
// 다른 Clock 빈은 두지 않는다. 사용처는 타입으로만 주입받는다(테스트가 @Primary로 바꿔 끼우기 때문)
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}

}
