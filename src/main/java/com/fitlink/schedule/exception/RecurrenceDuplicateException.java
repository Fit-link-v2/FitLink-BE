package com.fitlink.schedule.exception;

import com.fitlink.schedule.model.Weekday;
import java.util.List;

// 같은 요일 · 시각에 켜진 규칙이 이미 있다. 겹친 요일을 알려 주고 요청 전체를 저장하지 않는다
public class RecurrenceDuplicateException extends RuntimeException {

	private final List<Weekday> weekdays;

	public RecurrenceDuplicateException(List<Weekday> weekdays) {
		super("같은 요일 · 시각의 켜진 규칙이 있다: " + weekdays);
		this.weekdays = List.copyOf(weekdays);
	}

	public List<Weekday> weekdays() {
		return weekdays;
	}

}
