package com.fitlink.instructor.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

// 로그인 쿠키의 이름과 속성을 한 곳에 둔다. 이름은 환경마다 다르다(local은 SESSION, https 환경은 __Host- 접두사)
@Component
class AuthCookies {

	private final String sessionName;

	AuthCookies(@Value("${fitlink.auth.cookie.session}") String sessionName) {
		this.sessionName = sessionName;
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

}
