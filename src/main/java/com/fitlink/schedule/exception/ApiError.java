package com.fitlink.schedule.exception;

import com.fitlink.schedule.model.Weekday;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// 에러 응답 { code, message }. 코드에 따라 fields(400) · weekdays(규칙 중복 409)를 더한다
@JsonInclude(JsonInclude.Include.NON_NULL)
record ApiError(String code, String message, List<FieldProblem> fields, List<Weekday> weekdays) {

	static ApiError of(String code, String message) {
		return new ApiError(code, message, null, null);
	}

	// 앞의 확인에서 걸린 경우와 동시 요청이 DB 유니크에 걸린 경우가 같은 응답이 되도록 한 곳에서 만든다
	static ApiError recurrenceDuplicate(List<Weekday> weekdays) {
		return new ApiError("RECURRENCE_DUPLICATE", "같은 요일 · 시각의 켜진 규칙이 있다", null, weekdays);
	}

	record FieldProblem(String name, String reason) {
	}

}
