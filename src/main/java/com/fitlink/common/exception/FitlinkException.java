package com.fitlink.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// 서비스가 요청을 거절할 때 던지는 예외는 이것 하나다. 에러 코드가 응답의 상태 · code · message를 정하고,
// details는 응답 본문에 code · message와 같은 높이로 펼친다(예: "weekdays" → ["MON"]).
// 요청을 거절하는 정상 흐름이라 스택 트레이스를 기록하지 않는다. 처리기도 warn 한 줄만 남긴다
public class FitlinkException extends RuntimeException {

	// 응답 본문에서 code · message를 덮어쓰지 않도록 details 키로 쓰지 못하게 한다
	private static final Set<String> RESERVED = Set.of("code", "message");

	private final ErrorCode errorCode;

	private final Map<String, Object> details;

	public FitlinkException(ErrorCode errorCode) {
		this(errorCode, Map.of());
	}

	public FitlinkException(ErrorCode errorCode, Map<String, Object> details) {
		super(errorCode.message(), null, false, false);
		RESERVED.stream().filter(details::containsKey).findFirst().ifPresent(key -> {
			throw new IllegalArgumentException("details에 " + key + " 키를 쓸 수 없다");
		});
		this.errorCode = errorCode;
		this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

	public Map<String, Object> details() {
		return details;
	}

}
