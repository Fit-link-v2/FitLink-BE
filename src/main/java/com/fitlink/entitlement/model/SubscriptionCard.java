package com.fitlink.entitlement.model;

import java.time.LocalDate;

// 월 정액 카드. id는 월 정액 등록의 id이고, 상태는 등록 전체 기간으로 정한다.
// currentPeriod는 오늘을 덮는 주기이고 없으면 null이다(시작 전 · 끝난 뒤 · 아직 만들지 않은 주기)
public record SubscriptionCard(long id, PeriodUnit per, int count, LocalDate startsOn, LocalDate endsOn,
		CardState state, CurrentPeriod currentPeriod) implements Card {
}
