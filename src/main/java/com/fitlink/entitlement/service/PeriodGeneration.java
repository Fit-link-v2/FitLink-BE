package com.fitlink.entitlement.service;

import com.fitlink.entitlement.repository.SubscriptionRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

// 월 정액 하나의 주기별 수강권을 그 강사의 오픈 범위 끝까지 만든다. 몇 번 불러도 같은 주기는 한 번만 생긴다
@Service
public class PeriodGeneration {

	private final SubscriptionRepository subscriptions;

	PeriodGeneration(SubscriptionRepository subscriptions) {
		this.subscriptions = subscriptions;
	}

	// 부르는 쪽의 트랜잭션에 합류한다
	public void generate(long subscriptionId, LocalDate today) {
		subscriptions.generatePeriods(subscriptionId, today);
	}

}
