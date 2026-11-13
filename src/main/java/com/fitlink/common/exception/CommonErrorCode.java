package com.fitlink.common.exception;

import org.springframework.http.HttpStatus;

// 어느 모듈에서나 쓰는 에러 코드. 응답 code는 상수 이름이다
public enum CommonErrorCode implements ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력이 올바르지 않다"),

	// 경로의 id가 없거나 다른 강사의 것이다. 남의 것인지 없는 것인지는 구분해 알려 주지 않는다
	NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없다"),

	// 원인을 응답에 담지 않는다. 내부 구조가 드러나지 않도록 로그에만 남긴다
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 났다");

	private final HttpStatus status;

	private final String message;

	CommonErrorCode(HttpStatus status, String message) {
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
