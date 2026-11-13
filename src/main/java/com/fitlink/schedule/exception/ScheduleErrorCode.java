package com.fitlink.schedule.exception;

import com.fitlink.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

// 시간표 모듈의 에러 코드. 응답 code는 상수 이름이다
public enum ScheduleErrorCode implements ErrorCode {

	// 슬롯의 지금 상태 때문에 시각 변경 · 휴강을 받을 수 없다
	SLOT_CANCELED(HttpStatus.UNPROCESSABLE_CONTENT, "휴강한 수업이다"),

	CLASS_STARTED(HttpStatus.UNPROCESSABLE_CONTENT, "이미 시작한 수업이다"),

	SLOT_HAS_BOOKINGS(HttpStatus.UNPROCESSABLE_CONTENT, "예약이 있는 수업이다"),

	SLOT_TIME_TAKEN(HttpStatus.CONFLICT, "같은 시각에 수업이 있다"),

	// 겹친 요일을 details의 weekdays로 알려 주고 요청 전체를 저장하지 않는다
	RECURRENCE_DUPLICATE(HttpStatus.CONFLICT, "같은 요일 · 시각의 켜진 규칙이 있다");

	private final HttpStatus status;

	private final String message;

	ScheduleErrorCode(HttpStatus status, String message) {
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
