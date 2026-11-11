package com.fitlink.schedule.api;

import com.fitlink.schedule.application.NotFoundException;
import com.fitlink.schedule.application.RecurrenceDuplicateException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;

// 시간표 API의 예외를 에러 코드로 바꾼다. DB 제약 위반은 SQLState가 아니라 제약 이름으로 고르고, 대응표에 없으면 500으로 둔다
@RestControllerAdvice(basePackageClasses = ScheduleErrorHandler.class)
class ScheduleErrorHandler {

	private static final String VALIDATION_FAILED = "VALIDATION_FAILED";

	// 앞의 확인을 동시에 지나 DB 유니크에 걸린 경우다. 이때는 겹친 요일을 다시 읽을 수 없어 비워 둔다
	private static final Map<String, ApiError> BY_CONSTRAINT = Map.of(
			"recurrence_slot_uidx",
			ApiError.recurrenceDuplicate(List.of()),
			"class_slot_time_uidx", ApiError.of("SLOT_TIME_TAKEN", "같은 시각에 수업이 있다"));

	@ExceptionHandler
	ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
		List<ApiError.FieldProblem> fields = e.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new ApiError.FieldProblem(withoutIndex(error.getField()), error.getDefaultMessage()))
			.toList();
		return badRequest(fields);
	}

	@ExceptionHandler
	ResponseEntity<ApiError> invalid(InvalidRequestException e) {
		return badRequest(List.of(new ApiError.FieldProblem(e.field(), e.getMessage())));
	}

	// JSON 문법 오류 · 모르는 요일 · HH:mm이 아닌 시각. 어느 필드에서 났는지 Jackson 경로로 알려 준다.
	// 시각 파싱 실패는 가장 안쪽 원인이 DateTimeParseException이라, 원인을 바깥부터 따라가 Jackson 예외를 찾는다
	@ExceptionHandler
	ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
		String field = "body";
		for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof JacksonException jackson) {
				field = jackson.getPath()
					.stream()
					.map(JacksonException.Reference::getPropertyName)
					.filter(name -> name != null)
					.findFirst()
					.orElse(field);
				break;
			}
		}
		return badRequest(List.of(new ApiError.FieldProblem(field, "형식이 맞지 않는다")));
	}

	// 경로의 id가 숫자가 아니다
	@ExceptionHandler
	ResponseEntity<ApiError> invalid(MethodArgumentTypeMismatchException e) {
		return badRequest(List.of(new ApiError.FieldProblem(e.getName(), "형식이 맞지 않는다")));
	}

	@ExceptionHandler
	ResponseEntity<ApiError> notFound(NotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler
	ResponseEntity<ApiError> duplicate(RecurrenceDuplicateException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(ApiError.recurrenceDuplicate(e.weekdays()));
	}

	@ExceptionHandler
	ResponseEntity<ApiError> duplicate(DuplicateKeyException e) {
		String message = String.valueOf(e.getMostSpecificCause().getMessage());
		return BY_CONSTRAINT.entrySet()
			.stream()
			.filter(entry -> message.contains("\"" + entry.getKey() + "\""))
			.findFirst()
			.map(entry -> ResponseEntity.status(HttpStatus.CONFLICT).body(entry.getValue()))
			.orElseThrow(() -> e);
	}

	// 목록 원소의 오류는 weekdays[1]처럼 오는데, 클라이언트에는 필드 이름만 준다
	private static String withoutIndex(String field) {
		return field.replaceFirst("\\[.*$", "");
	}

	private static ResponseEntity<ApiError> badRequest(List<ApiError.FieldProblem> fields) {
		return ResponseEntity.badRequest().body(new ApiError(VALIDATION_FAILED, "입력이 올바르지 않다", fields, null));
	}

}
