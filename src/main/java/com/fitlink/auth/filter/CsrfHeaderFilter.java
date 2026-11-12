package com.fitlink.auth.filter;

import com.fitlink.common.exception.ErrorResponse;
import com.fitlink.common.exception.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 상태를 바꾸는 요청(GET · HEAD · OPTIONS 외)은 {@code X-CSRF: 1} 헤더가 있어야 통과한다.
 * 다른 사이트의 form은 커스텀 헤더를 붙일 수 없고, fetch로 붙이면 CORS 사전 요청에서 막히므로 위조 요청을 걸러 낸다.
 * 인증 확인 뒤에 두어, 인증이 필요한 경로는 401이 403보다 먼저 나간다.
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

	private final ErrorResponseWriter errors;

	public CsrfHeaderFilter(ErrorResponseWriter errors) {
		this.errors = errors;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (SAFE_METHODS.contains(request.getMethod()) || "1".equals(request.getHeader("X-CSRF"))) {
			chain.doFilter(request, response);
			return;
		}
		errors.write(response, HttpStatus.FORBIDDEN,
				new ErrorResponse("CSRF_HEADER_MISSING", "X-CSRF 헤더가 필요합니다."));
	}

}
