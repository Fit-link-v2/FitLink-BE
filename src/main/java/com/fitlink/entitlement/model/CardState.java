package com.fitlink.entitlement.model;

import java.time.LocalDate;

// 카드의 상태. 날짜로만 정한다. 남은 횟수가 0이어도 만료가 아니다
public enum CardState {

	NOT_STARTED, ACTIVE, EXPIRED;

	// 시작일이 오늘 뒤면 NOT_STARTED, 종료일이 오늘 앞이면 EXPIRED. 시작일 · 종료일 당일은 ACTIVE다
	public static CardState of(LocalDate startsOn, LocalDate endsOn, LocalDate today) {
		if (startsOn.isAfter(today)) {
			return NOT_STARTED;
		}
		if (endsOn.isBefore(today)) {
			return EXPIRED;
		}
		return ACTIVE;
	}

}
