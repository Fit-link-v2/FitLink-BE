package com.fitlink.entitlement.model;

import java.time.LocalDate;

// 수강권 등록 입력. 종류마다 record 하나다. 기간은 다른 종류와 겹치는지 볼 때 쓰는 등록 전체 기간이다
public sealed interface Registration permits PassRegistration, SubscriptionRegistration {

	LocalDate startsOn();

	LocalDate endsOn();

}
