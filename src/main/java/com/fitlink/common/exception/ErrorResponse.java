package com.fitlink.common.exception;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

// 모든 API가 같은 모양으로 쓰는 에러 응답 본문 { code, message }.
// details는 따로 감싸지 않고 code · message 옆에 펼친다(예: { code, message, weekdays })
public record ErrorResponse(String code, String message, @JsonAnyGetter Map<String, Object> details) {

	// 에러 코드 enum이 없는 상태(Spring이 정한 405 · 415 등)의 문구. 응답 code는 상태 이름(METHOD_NOT_ALLOWED 등)이다
	private static final Map<Integer, String> STATUS_MESSAGES = Map.of(
			405, "지원하지 않는 메서드다",
			406, "줄 수 있는 응답 형식이 없다",
			413, "요청이 너무 크다",
			415, "지원하지 않는 요청 형식이다",
			503, "잠시 후 다시 시도한다");

	public static ErrorResponse of(ErrorCode code) {
		return of(code, Map.of());
	}

	public static ErrorResponse of(ErrorCode code, Map<String, Object> details) {
		return new ErrorResponse(code.code(), code.message(), details);
	}

	// 상태 코드만 아는 경우. 400 · 404 · 500은 공통 에러 코드를 쓰고, VALIDATION_FAILED는 언제나 fields를 둔다
	public static ErrorResponse forStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> of(CommonErrorCode.VALIDATION_FAILED, Map.of("fields", List.of()));
			case 404 -> of(CommonErrorCode.NOT_FOUND);
			case 500 -> of(CommonErrorCode.INTERNAL_ERROR);
			default -> {
				HttpStatus known = HttpStatus.resolve(status.value());
				String code = (known != null) ? known.name() : "HTTP_" + status.value();
				yield new ErrorResponse(code, STATUS_MESSAGES.getOrDefault(status.value(), "요청을 처리할 수 없다"),
						Map.of());
			}
		};
	}

}
