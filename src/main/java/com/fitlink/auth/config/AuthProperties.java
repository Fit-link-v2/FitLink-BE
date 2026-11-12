package com.fitlink.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code fitlink.auth.*} 설정. auth 모듈의 api(쿠키 · 카카오 주소)와 infra(카카오 API 호출)가 같이 쓰므로 모듈 최상위 패키지에 둔다.
 * 값이 비어 있으면 앱이 뜨지 않는다. 카카오 키는 환경변수로만 받는다.
 */
@ConfigurationProperties("fitlink.auth")
public record AuthProperties(Kakao kakao, Cookie cookie, String successUrl, String failureUrl) {

	public record Kakao(String clientId, String clientSecret, String redirectUri) {
	}

	// local은 SESSION · KAKAO-STATE, https 환경은 __Host-Http- 접두사를 붙인다
	public record Cookie(String session, String state) {
	}

}
