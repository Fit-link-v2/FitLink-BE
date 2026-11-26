package com.fitlink.entitlement;

import java.time.LocalDate;

/**
 * 다른 모듈이 받는 수강권 카드 한 장. 회원 화면 · 강사 화면의 카드와 같은 모양이다. 상태 · 주기 단위는 모듈 안의 enum 대신
 * 이름으로 준다(state: NOT_STARTED · ACTIVE · EXPIRED, per: WEEK · FOUR_WEEKS).
 */
public sealed interface EntitlementCardInfo {

	// 횟수권은 entitlement 행의 id, 월 정액은 등록(subscription)의 id다. 두 번호는 서로 겹칠 수 있다
	long id();

	String state();

	// 횟수권. remaining = maxCount − 사용 횟수
	record Pass(long id, LocalDate startsOn, LocalDate endsOn, int maxCount, int remaining, String state)
			implements EntitlementCardInfo {
	}

	// 월 정액 등록 하나. currentPeriod는 오늘을 덮는 주기이고 없으면 null이다
	record Subscription(long id, String per, int count, LocalDate startsOn, LocalDate endsOn, String state,
			Period currentPeriod) implements EntitlementCardInfo {
	}

	// 월 정액의 주기 하나. entitlementId는 그 주기 행의 id라 잔여 수정이 이 id로 한다
	record Period(long entitlementId, LocalDate startsOn, LocalDate endsOn, int maxCount, int remaining) {
	}

}
