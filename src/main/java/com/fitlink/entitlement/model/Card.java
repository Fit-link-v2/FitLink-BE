package com.fitlink.entitlement.model;

import java.time.LocalDate;

// 회원 화면 · 강사 화면에 같은 모양으로 보이는 수강권 카드. 횟수권은 1장이 행 1개, 월 정액은 등록 1건이 카드 1장이다.
// id는 횟수권이면 entitlement 행, 월 정액이면 subscription 행의 id라 종류가 다르면 같은 번호일 수 있다
public sealed interface Card permits PassCard, SubscriptionCard {

	long id();

	LocalDate startsOn();

	LocalDate endsOn();

	CardState state();

}
