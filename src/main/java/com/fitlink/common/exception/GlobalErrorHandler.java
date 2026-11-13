package com.fitlink.common.exception;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

/**
 * 모든 컨트롤러의 예외를 { code, message } 응답으로 바꾼다.
 * <ul>
 * <li>{@link FitlinkException}: 에러 코드가 정한 상태로 응답한다</li>
 * <li>DB 제약 위반: 제약 이름을 모듈의 {@link ConstraintErrors}에 물어 바꾼다. 아무 모듈도 모르면 500</li>
 * <li>Spring MVC 예외(검증 실패, 405, 415 등): 부모 {@link ResponseEntityExceptionHandler}가 원래 상태를 정하고,
 * 응답 본문만 우리 모양으로 바꾼다. 400 VALIDATION_FAILED는 언제나 fields를 둔다</li>
 * <li>그 밖의 예외: 코드 버그로 보고 500. 원인은 로그에만 남긴다</li>
 * </ul>
 * 4xx는 warn, 5xx는 error로 남긴다. 4xx 로그에는 요청 값이 들어갈 수 있는 예외 메시지 대신 예외 종류와 주소만 쓴다.
 * 필터에서 난 예외는 컨트롤러를 거치지 않아 여기로 오지 않는다({@link ErrorResponseWriter}, {@link ErrorPageController})
 */
@RestControllerAdvice
public class GlobalErrorHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

	// DB 에러 메시지에서 따옴표로 감싼 이름을 모두 꺼낸다. 메시지 문구는 DB 언어 설정(lc_messages)에 따라 바뀌지만
	// 제약 이름은 그대로 따옴표 안에 온다. 테이블 · 열 이름도 섞여 나오므로 모듈이 아는 이름인지 차례로 묻는다
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

	private static final String FORMAT_MISMATCH = "형식이 맞지 않는다";

	private final List<ConstraintErrors> constraintErrors;

	GlobalErrorHandler(List<ConstraintErrors> constraintErrors) {
		this.constraintErrors = constraintErrors;
	}

	@ExceptionHandler
	ResponseEntity<Object> handleFitlink(FitlinkException e) {
		log.warn("[{}] {} {}", e.errorCode().code(), e.getMessage(), e.details());
		return ResponseEntity.status(e.errorCode().status()).body(ErrorResponse.of(e.errorCode(), e.details()));
	}

	// DuplicateKeyException(유니크)도 이 타입이다. CHECK · 외래 키 위반까지 같은 대응표로 고른다
	@ExceptionHandler
	ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException e, WebRequest request) {
		return find(e).map(this::handleFitlink).orElseGet(() -> internalError(e, request));
	}

	// 위 처리기와 부모가 받지 않은 나머지 전부. Security 예외는 받지 않고 다시 던져,
	// Security 필터(ExceptionTranslationFilter)가 401 · 403으로 바꾸게 둔다
	@ExceptionHandler
	ResponseEntity<Object> handleUnknown(Exception e, WebRequest request) throws Exception {
		if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
			throw e;
		}
		return internalError(e, request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldProblem> fields = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new FieldProblem(withoutIndex(error.getField()), error.getDefaultMessage()))
			.toList();
		return validationFailed(ex, fields, headers, request);
	}

	// 경로 값 · 쿼리 파라미터의 제약(@Min 등) 위반. 필드 이름은 컨트롤러 메서드의 매개변수 이름이다
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldProblem> fields = ex.getParameterValidationResults()
			.stream()
			.map(result -> new FieldProblem(result.getMethodParameter().getParameterName(),
					result.getResolvableErrors().getFirst().getDefaultMessage()))
			.toList();
		return validationFailed(ex, fields, headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return validationFailed(ex, List.of(new FieldProblem(ex.getParameterName(), "값을 보낸다")), headers,
				request);
	}

	@Override
	protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (ex instanceof MissingRequestHeaderException header) {
			return validationFailed(ex, List.of(new FieldProblem(header.getHeaderName(), "값을 보낸다")), headers,
					request);
		}
		return super.handleServletRequestBindingException(ex, headers, status, request);
	}

	// JSON 문법 오류 · 모르는 요일 · HH:mm이 아닌 시각. 어느 필드에서 났는지 Jackson 경로로 알려 준다.
	// 시각 파싱 실패는 가장 안쪽 원인이 DateTimeParseException이라, 원인을 바깥부터 따라가 Jackson 예외를 찾는다
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String field = "body";
		for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
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
		return validationFailed(ex, List.of(new FieldProblem(field, FORMAT_MISMATCH)), headers, request);
	}

	// 경로의 id가 숫자가 아니다
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String field = (ex instanceof MethodArgumentTypeMismatchException argument) ? argument.getName()
				: ex.getPropertyName();
		return validationFailed(ex, List.of(new FieldProblem(field, FORMAT_MISMATCH)), headers, request);
	}

	// 부모가 처리한 Spring MVC 예외는 모두 여기를 지나 나간다. 로그와 응답 본문을 이 한 곳에서 정한다
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		if (status.is5xxServerError()) {
			log.error("[{}] {} {}", status.value(), ex.getClass().getSimpleName(), request.getDescription(false), ex);
		}
		else {
			log.warn("[{}] {} {}", status.value(), ex.getClass().getSimpleName(), request.getDescription(false));
		}
		Object ours = (body instanceof ErrorResponse) ? body : ErrorResponse.forStatus(status);
		return super.handleExceptionInternal(ex, ours, headers, status, request);
	}

	private ResponseEntity<Object> validationFailed(Exception ex, List<FieldProblem> fields, HttpHeaders headers,
			WebRequest request) {
		ErrorResponse body = ErrorResponse.of(CommonErrorCode.VALIDATION_FAILED, Map.of("fields", fields));
		return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
	}

	private Optional<FitlinkException> find(DataIntegrityViolationException e) {
		String message = String.valueOf(e.getMostSpecificCause().getMessage());
		return QUOTED.matcher(message)
			.results()
			.map(match -> match.group(1))
			.flatMap(name -> constraintErrors.stream().flatMap(errors -> errors.find(name).stream()))
			.findFirst();
	}

	private static ResponseEntity<Object> internalError(Exception e, WebRequest request) {
		log.error("처리하지 못한 예외 {}", request.getDescription(false), e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR));
	}

	// 목록 원소의 오류는 weekdays[1]처럼 오는데, 클라이언트에는 필드 이름만 준다
	private static String withoutIndex(String field) {
		return field.replaceFirst("\\[.*$", "");
	}

}
