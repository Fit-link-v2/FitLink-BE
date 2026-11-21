package com.fitlink.entitlement.model;

import java.time.LocalDate;

// 횟수권 카드. remaining = maxCount − 사용 횟수
public record PassCard(long id, LocalDate startsOn, LocalDate endsOn, int maxCount, int remaining, CardState state) {
}
