package com.fitlink.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// application.yml을 실제로 읽어 카카오 키가 비었을 때 앱이 뜨지 않는지 본다.
// 키가 없으면 로그인을 누른 뒤에야 카카오가 거절해 원인을 찾기 어렵기 때문이다
class AuthPropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(new ConfigDataApplicationContextInitializer())
		.withUserConfiguration(Config.class);

	@Test
	void 카카오_키가_있으면_뜬다() {
		runner.withPropertyValues("KAKAO_CLIENT_ID=id", "KAKAO_CLIENT_SECRET=secret")
			.run(context -> assertThat(context).hasNotFailed()
				.getBean(AuthProperties.class)
				.extracting(p -> p.kakao().clientId())
				.isEqualTo("id"));
	}

	@Test
	void 카카오_REST_API_키가_비어_있으면_뜨지_않는다() {
		runner.withPropertyValues("KAKAO_CLIENT_ID=", "KAKAO_CLIENT_SECRET=secret")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("kakao.clientId")
				.hasMessageContaining("KAKAO_CLIENT_ID"));
	}

	@Test
	void 카카오_client_secret이_비어_있으면_뜨지_않는다() {
		runner.withPropertyValues("KAKAO_CLIENT_ID=id", "KAKAO_CLIENT_SECRET=")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("kakao.clientSecret")
				.hasMessageContaining("KAKAO_CLIENT_SECRET"));
	}

	@Test
	void 카카오_설정_묶음이_통째로_없으면_뜨지_않는다() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
			.withPropertyValues("fitlink.auth.cookie.session=SESSION", "fitlink.auth.cookie.state=KAKAO-STATE",
					"fitlink.auth.success-url=http://localhost/", "fitlink.auth.failure-url=http://localhost/?f")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("field 'kakao'"));
	}

	@EnableConfigurationProperties(AuthProperties.class)
	static class Config {

	}

}
