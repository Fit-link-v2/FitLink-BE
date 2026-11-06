package com.fitlink.instructor.api;

import com.fitlink.instructor.application.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션 쿠키가 유효하면 강사 id를 인증 정보로 채운다. 401은 직접 보내지 않고 Security 허용 규칙이 정한다.
 * 그래야 인증이 필요 없는 경로는 쿠키가 틀려도 그대로 통과한다.
 * {@code config}가 Security 필터 체인에 끼우므로 이 클래스만 다른 모듈에 공개한다.
 */
@Component
@NamedInterface("session-filter")
public class SessionAuthenticationFilter extends OncePerRequestFilter {

	private final SessionService sessions;

	private final AuthCookies cookies;

	SessionAuthenticationFilter(SessionService sessions, AuthCookies cookies) {
		this.sessions = sessions;
		this.cookies = cookies;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		cookies.sessionId(request)
			.flatMap(sessions::authenticate)
			.ifPresent(instructorId -> SecurityContextHolder.getContext()
				.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(instructorId, null,
						AuthorityUtils.NO_AUTHORITIES)));
		chain.doFilter(request, response);
	}

}
