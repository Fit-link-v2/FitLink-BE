package com.fitlink.entitlement.exception;

import com.fitlink.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

// 수강권 모듈의 에러 코드. 응답 code는 상수 이름이다
public enum EntitlementErrorCode implements ErrorCode {

	// 수강 종료(ENDED) 회원에게는 등록하지 않는다. 다시 활성화한 뒤 등록한다
	MEMBER_ENDED(HttpStatus.UNPROCESSABLE_CONTENT, "수강을 종료한 회원이다");

	private final HttpStatus status;

	private final String message;

	EntitlementErrorCode(HttpStatus status, String message) {
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
