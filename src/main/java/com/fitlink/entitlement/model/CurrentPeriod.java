package com.fitlink.entitlement.model;

import java.time.LocalDate;

// 월 정액에서 오늘을 덮는 주기 하나. entitlementId는 그 주기 행의 id라 잔여 수정이 이 id로 한다
public record CurrentPeriod(long entitlementId, LocalDate startsOn, LocalDate endsOn, int maxCount, int remaining) {
}
