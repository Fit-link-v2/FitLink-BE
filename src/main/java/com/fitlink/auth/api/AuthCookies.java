package com.fitlink.auth.api;

import com.fitlink.auth.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

// 로그인 쿠키의 이름과 속성을 한 곳에 둔다. 이름은 설정값이다(AuthProperties)
@Component
class AuthCookies {

	private static final Duration SESSION_MAX_AGE = Duration.ofDays(14);

	private final String sessionName;

	private final String stateName;

	AuthCookies(AuthProperties properties) {
		this.sessionName = properties.cookie().session();
		this.stateName = properties.cookie().state();
	}

	Optional<String> sessionId(HttpServletRequest request) {
		return value(request, sessionName);
	}

	Optional<String> state(HttpServletRequest request) {
		return value(request, stateName);
	}

	// 세션 만료(14일)와 같게 둔다. 브라우저를 닫아도 로그인이 유지된다
	ResponseCookie session(String sessionId) {
		return cookie(sessionName, sessionId, SESSION_MAX_AGE);
	}

	// 같은 이름 · Path로 Max-Age=0을 보내야 브라우저가 지운다
	ResponseCookie expiredSession() {
		return cookie(sessionName, "", Duration.ZERO);
	}

	ResponseCookie expiredState() {
		return cookie(stateName, "", Duration.ZERO);
	}

	// 카카오에서 돌아오는 콜백은 다른 사이트에서 오는 top-level GET이라 SameSite=Lax여야 쿠키가 따라온다.
	// 로그인을 10분 안에 끝내지 않으면 사라진다
	ResponseCookie state(String state) {
		return cookie(stateName, state, Duration.ofMinutes(10));
	}

	private static Optional<String> value(HttpServletRequest request, String name) {
		return Optional.ofNullable(WebUtils.getCookie(request, name)).map(Cookie::getValue);
	}

	private static ResponseCookie cookie(String name, String value, Duration maxAge) {
		return ResponseCookie.from(name, value)
			.maxAge(maxAge)
			.path("/")
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.build();
	}

}
