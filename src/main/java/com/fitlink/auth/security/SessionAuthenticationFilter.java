package com.fitlink.auth.security;

import com.fitlink.auth.config.AuthCookies;
import com.fitlink.auth.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션 쿠키가 유효하면 강사 id를 인증 정보로 채운다. 401은 직접 보내지 않고 Security 허용 규칙이 정한다.
 * 그래야 인증이 필요 없는 경로는 쿠키가 틀려도 그대로 통과한다.
 */
@Component
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
		Optional<String> sessionId = cookies.sessionId(request);
		sessionId.flatMap(sessions::authenticate).ifPresent(instructorId -> {
			SecurityContextHolder.getContext()
				.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(instructorId, null,
						AuthorityUtils.NO_AUTHORITIES));
			// DB에서 만료를 14일 뒤로 미뤘으니 쿠키 Max-Age도 같이 미룬다. 로그인 · 로그아웃 응답은
			// 컨트롤러가 ResponseEntity로 준 SESSION 쿠키가 이 헤더를 덮어쓴다
			response.addHeader(HttpHeaders.SET_COOKIE, cookies.session(sessionId.get()).toString());
		});
		chain.doFilter(request, response);
	}

}
