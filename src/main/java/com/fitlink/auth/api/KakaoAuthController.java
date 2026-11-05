package com.fitlink.auth.api;

import com.fitlink.auth.AuthProperties;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
class KakaoAuthController {

	private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AuthCookies cookies;

	private final AuthProperties.Kakao kakao;

	KakaoAuthController(AuthCookies cookies, AuthProperties properties) {
		this.cookies = cookies;
		this.kakao = properties.kakao();
	}

	// state를 쿠키와 카카오 주소에 같이 실어 보낸다. 콜백에서 두 값이 같아야 우리가 시작한 로그인으로 인정한다
	@GetMapping("/auth/kakao")
	ResponseEntity<Void> start() {
		String state = newState();
		URI location = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
			.queryParam("client_id", kakao.clientId())
			.queryParam("redirect_uri", kakao.redirectUri())
			.queryParam("response_type", "code")
			.queryParam("state", state)
			.encode()
			.build()
			.toUri();
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(location)
			.header(HttpHeaders.SET_COOKIE, cookies.state(state).toString())
			.build();
	}

	private static String newState() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
