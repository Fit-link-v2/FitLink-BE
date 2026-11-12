package com.fitlink.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code fitlink.auth.*} 설정. auth 모듈의 api(쿠키 · 카카오 주소)와 infra(카카오 API 호출)가 같이 쓰므로 모듈 최상위 패키지에 둔다.
 * 값이 비어 있으면 앱이 뜨지 않는다. 카카오 키는 환경변수로만 받는다.
 */
// @Valid는 null인 묶음을 건너뛰므로 묶음 자체가 빠진 경우는 @NotNull이 막는다
@Validated
@ConfigurationProperties("fitlink.auth")
public record AuthProperties(@NotNull @Valid Kakao kakao, @NotNull @Valid Cookie cookie,
		@NotBlank String successUrl, @NotBlank String failureUrl) {

	public record Kakao(@NotBlank(message = "환경변수 KAKAO_CLIENT_ID를 채운다(.env.example 참고)") String clientId,
			@NotBlank(message = "환경변수 KAKAO_CLIENT_SECRET을 채운다(.env.example 참고)") String clientSecret,
			@NotBlank String redirectUri) {
	}

	// local은 SESSION · KAKAO-STATE, https 환경은 __Host-Http- 접두사를 붙인다
	public record Cookie(@NotBlank String session, @NotBlank String state) {
	}

}
