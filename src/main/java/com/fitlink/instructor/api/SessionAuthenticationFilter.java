package com.fitlink.instructor.api;

import com.fitlink.instructor.application.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * 세션 쿠키가 유효하면 강사 id를 인증 정보로 채운다. 401은 직접 보내지 않고 Security 허용 규칙이 정한다. 그래야 인증이 필요 없는 경로는 쿠키가 틀려도 그대로 통과한다.
 * {@code config}가 필터 체인에 끼우므로 이 클래스만 공개한다. Bean으로 등록하지 않는다(서블릿 필터로 한 번 더 걸리지 않게).
 */
@NamedInterface("session-filter")
public class SessionAuthenticationFilter extends OncePerRequestFilter {

	static final String COOKIE = "SESSION";

	private final SessionService sessions;

	public SessionAuthenticationFilter(SessionService sessions) {
		this.sessions = sessions;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Cookie cookie = WebUtils.getCookie(request, COOKIE);
		if (cookie != null) {
			sessions.authenticate(cookie.getValue())
				.ifPresent(instructorId -> SecurityContextHolder.getContext()
					.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(instructorId, null,
							AuthorityUtils.NO_AUTHORITIES)));
		}
		chain.doFilter(request, response);
	}

}
