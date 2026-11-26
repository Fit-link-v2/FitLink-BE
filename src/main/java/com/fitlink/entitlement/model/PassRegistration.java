package com.fitlink.entitlement.model;

import java.time.LocalDate;

// 횟수권 등록 입력. 시작일 <= 종료일, 횟수 1 ~ 999는 요청을 읽을 때 이미 확인했다
public record PassRegistration(LocalDate startsOn, LocalDate endsOn, int maxCount) implements Registration {
}
