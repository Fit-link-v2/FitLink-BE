package com.fitlink.auth.api;

import com.fitlink.auth.AuthProperties;
import com.fitlink.auth.application.KakaoLoginException;
import com.fitlink.auth.application.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
class KakaoAuthController {

	private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Logger log = LoggerFactory.getLogger(KakaoAuthController.class);

	private final AuthCookies cookies;

	private final AuthProperties properties;

	private final AuthProperties.Kakao kakao;

	private final LoginService login;

	KakaoAuthController(AuthCookies cookies, AuthProperties properties, LoginService login) {
		this.cookies = cookies;
		this.properties = properties;
		this.kakao = properties.kakao();
		this.login = login;
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

	// 목적지는 설정값으로만 정한다. 요청 파라미터를 보지 않는다.
	// state 쿠키는 결과와 상관없이 지운다. 한 번 쓴 state를 다시 쓰지 못하게 한다
	@GetMapping("/auth/kakao/callback")
	ResponseEntity<Void> callback(@RequestParam(required = false) String code,
			@RequestParam(required = false) String state, @RequestParam(required = false) String error,
			HttpServletRequest request) {
		// error는 강사가 동의를 취소했거나 카카오가 거절한 경우다
		if (error != null) {
			// 누구나 이 주소를 부를 수 있으므로 카카오 에러 코드 형식(영문 소문자 · 밑줄)일 때만 값을 남긴다
			return fail("카카오가 error를 돌려줌: " + (error.matches("[a-z_]{1,50}") ? error : "형식 밖 값"));
		}
		if (code == null || !sameState(state, cookies.state(request))) {
			return fail("code 없음 또는 state 불일치");
		}
		try {
			String sessionId = login.login(code, cookies.sessionId(request));
			return redirect(properties.successUrl(), cookies.expiredState(), cookies.session(sessionId));
		}
		catch (KakaoLoginException e) {
			return fail(e.getMessage());
		}
	}

	// 원인만 남긴다. code · state · 토큰 값은 로그에 쓰지 않는다
	private ResponseEntity<Void> fail(String reason) {
		log.warn("카카오 로그인 실패: {}", reason);
		return redirect(properties.failureUrl(), cookies.expiredState());
	}

	// 길이 · 내용에 따라 비교 시간이 달라지지 않게 MessageDigest.isEqual로 비교한다
	private static boolean sameState(String fromKakao, Optional<String> fromCookie) {
		return fromKakao != null && fromCookie.isPresent() && MessageDigest
			.isEqual(fromKakao.getBytes(StandardCharsets.US_ASCII), fromCookie.get().getBytes(StandardCharsets.US_ASCII));
	}

	private static ResponseEntity<Void> redirect(String url, ResponseCookie... setCookies) {
		ResponseEntity.HeadersBuilder<?> response = ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url));
		for (ResponseCookie cookie : setCookies) {
			response.header(HttpHeaders.SET_COOKIE, cookie.toString());
		}
		return response.build();
	}

	private static String newState() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
