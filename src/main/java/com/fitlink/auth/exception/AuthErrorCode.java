package com.fitlink.auth.exception;

import com.fitlink.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

// 로그인 · 세션 필터가 쓰는 에러 코드. 필터는 컨트롤러 밖이라 ErrorResponseWriter로 직접 응답한다
public enum AuthErrorCode implements ErrorCode {

	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),

	CSRF_HEADER_MISSING(HttpStatus.FORBIDDEN, "X-CSRF 헤더가 필요합니다.");

	private final HttpStatus status;

	private final String message;

	AuthErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public HttpStatus status() {
		return status;
	}

	@Override
	public String message() {
		return message;
	}

}
