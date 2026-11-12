package com.fitlink.schedule.exception;

// 어노테이션으로 표현하기 어려운 입력 검사(요일 중복, 지난 시각 등)가 실패했다. 400과 어느 필드인지로 바꾼다
public class InvalidRequestException extends RuntimeException {

	private final String field;

	public InvalidRequestException(String field, String reason) {
		super(reason);
		this.field = field;
	}

	public String field() {
		return field;
	}

}
