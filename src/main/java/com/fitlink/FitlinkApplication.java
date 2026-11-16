package com.fitlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 아이디 · 비밀번호 로그인을 쓰지 않으므로 Spring Security 기본 계정(로그에 찍히는 임시 비밀번호)을 만들지 않는다
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class FitlinkApplication {

	public static void main(String[] args) {
		SpringApplication.run(FitlinkApplication.class, args);
	}

	// 자정 작업(@Scheduled)을 켠다. 통합 테스트는 fitlink.scheduling.enabled=false로 꺼서,
	// 자정을 넘겨 도는 CI에서 실제 작업이 테스트 도중 데이터를 바꾸지 않게 한다. 테스트는 작업 메서드를 직접 부른다.
	// 이 설정은 시각에 맞춰 도는 작업만 끈다. 앱 시작 때의 회차 생성은 그대로 돈다
	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	@ConditionalOnProperty(name = "fitlink.scheduling.enabled", havingValue = "true", matchIfMissing = true)
	static class Scheduling {

	}

}
