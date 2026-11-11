package com.fitlink.schedule.api;

// 어노테이션으로 표현하기 어려운 입력 검사(요일 중복 등)가 실패했을 때 400으로 바꾼다
class InvalidRequestException extends RuntimeException {

	private final String field;

	InvalidRequestException(String field, String reason) {
		super(reason);
		this.field = field;
	}

	String field() {
		return field;
	}

}
