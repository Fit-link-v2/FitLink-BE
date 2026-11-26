package com.fitlink.entitlement.model;

import java.time.LocalDate;

// 월 정액 등록 입력. 횟수 1 ~ 99, 등록 기간 weeks는 4의 배수 4 ~ 48이고 요청을 읽을 때 이미 확인했다.
// 주기 수와 종료일은 요청으로 받지 않고 여기서 계산한다. weeks가 4의 배수라 두 방식 모두 마지막 주기의 끝이 종료일과 같다
public record SubscriptionRegistration(PeriodUnit per, int count, LocalDate startsOn, int weeks)
		implements Registration {

	// WEEK면 weeks, FOUR_WEEKS면 weeks / 4
	public int periodCount() {
		return weeks * 7 / per.days();
	}

	// 시작일 + 7 × weeks − 1일
	@Override
	public LocalDate endsOn() {
		return startsOn.plusDays(7L * weeks - 1);
	}

}
