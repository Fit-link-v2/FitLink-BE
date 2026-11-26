package com.fitlink.entitlement.exception;

import com.fitlink.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

// 수강권 모듈의 에러 코드. 응답 code는 상수 이름이다
public enum EntitlementErrorCode implements ErrorCode {

	// 수강 종료(ENDED) 회원에게는 등록하지 않는다. 다시 활성화한 뒤 등록한다
	MEMBER_ENDED(HttpStatus.UNPROCESSABLE_CONTENT, "수강을 종료한 회원이다"),

	// 만료된 다른 종류와 기간이 겹친다
	ENTITLEMENT_PERIOD_OVERLAP(HttpStatus.UNPROCESSABLE_CONTENT, "다른 종류의 수강권과 기간이 겹친다"),

	// 만료되지 않은 다른 종류가 있다. 기간이 겹치든 아니든 막고, 등록할 수 있는 첫날을 details의 availableFrom으로 준다
	ENTITLEMENT_OTHER_KIND_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "다른 종류의 수강권이 아직 끝나지 않았다");

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
