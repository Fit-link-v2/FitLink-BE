package com.fitlink.entitlement.model;

import java.time.LocalDate;

// 등록하려는 종류와 다른 종류의 수강권을 한 번에 본 결과.
// activeUntil은 만료되지 않은 것들 중 가장 늦은 종료일(없으면 null), overlaps는 기간이 겹치는 것이 하나라도 있는지다
public record OtherKind(LocalDate activeUntil, boolean overlaps) {
}
