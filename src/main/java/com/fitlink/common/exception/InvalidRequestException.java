package com.fitlink.common.exception;

import java.util.List;
import java.util.Map;

// 어노테이션으로 표현하기 어려운 입력 검사(요일 중복, 지난 시각 등)가 실패했다. 400과 어느 필드인지로 바꾼다
public class InvalidRequestException extends FitlinkException {

	public InvalidRequestException(String field, String reason) {
		super(CommonErrorCode.VALIDATION_FAILED, Map.of("fields", List.of(new FieldProblem(field, reason))));
	}

}
