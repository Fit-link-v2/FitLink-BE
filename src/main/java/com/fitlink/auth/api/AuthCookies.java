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

	private final String sessionName;

	private final String stateName;

	AuthCookies(AuthProperties properties) {
		this.sessionName = properties.cookie().session();
		this.stateName = properties.cookie().state();
	}

	Optional<String> sessionId(HttpServletRequest request) {
		return Optional.ofNullable(WebUtils.getCookie(request, sessionName)).map(Cookie::getValue);
	}

	// 같은 이름 · Path로 Max-Age=0을 보내야 브라우저가 지운다
	ResponseCookie expiredSession() {
		return ResponseCookie.from(sessionName, "")
			.maxAge(0)
			.path("/")
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.build();
	}

	// 카카오에서 돌아오는 콜백은 다른 사이트에서 오는 top-level GET이라 SameSite=Lax여야 쿠키가 따라온다.
	// 로그인을 10분 안에 끝내지 않으면 사라진다
	ResponseCookie state(String state) {
		return ResponseCookie.from(stateName, state)
			.maxAge(Duration.ofMinutes(10))
			.path("/")
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.build();
	}

}
