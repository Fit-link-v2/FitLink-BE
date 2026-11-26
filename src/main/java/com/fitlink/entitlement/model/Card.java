package com.fitlink.entitlement.model;

// 회원 화면 · 강사 화면에 같은 모양으로 보이는 수강권 카드. 횟수권은 1장이 행 1개, 월 정액은 등록 1건이 카드 1장이다
public sealed interface Card permits PassCard, SubscriptionCard {
}
