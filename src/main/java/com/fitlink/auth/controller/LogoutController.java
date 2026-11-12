package com.fitlink.auth.controller;

import com.fitlink.auth.config.AuthCookies;
import com.fitlink.auth.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LogoutController {

	private final SessionService sessions;

	private final AuthCookies cookies;

	LogoutController(SessionService sessions, AuthCookies cookies) {
		this.sessions = sessions;
		this.cookies = cookies;
	}

	// 쿠키가 없거나 이미 무효인 세션이어도 204. 브라우저 쿠키는 항상 지운다
	@PostMapping("/auth/logout")
	ResponseEntity<Void> logout(HttpServletRequest request) {
		cookies.sessionId(request).ifPresent(sessions::revoke);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.expiredSession().toString()).build();
	}

}
