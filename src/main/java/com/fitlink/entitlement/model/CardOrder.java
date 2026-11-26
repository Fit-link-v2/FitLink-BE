package com.fitlink.entitlement.model;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * 카드 순서. 맨 앞이 다음에 차감될 수강권이다.
 * <ol>
 * <li>ACTIVE이고 지금 차감할 수 있는 것: 끝 날짜 이른 순. 횟수권은 종료일, 월 정액은 지금 주기의 끝 날짜다
 * <li>ACTIVE이지만 차감할 수 없는 것(잔여 0, 지금 주기가 없는 월 정액): 1과 같은 끝 날짜 이른 순. 지금 주기가 없는 월 정액은
 * 등록 종료일을 쓴다
 * <li>NOT_STARTED: 시작일 이른 순
 * <li>EXPIRED: 종료일 늦은 순
 * </ol>
 * 기준 날짜가 같으면 횟수권이 먼저이고, 같은 종류끼리는 id 순이다
 */
public final class CardOrder {

	public static final Comparator<Card> NEXT_TO_DEDUCT = Comparator.comparingInt(CardOrder::group)
		.thenComparingLong(CardOrder::dateKey)
		.thenComparingInt(card -> (card instanceof PassCard) ? 0 : 1)
		.thenComparingLong(Card::id);

	private CardOrder() {
	}

	private static int group(Card card) {
		return switch (card.state()) {
			case ACTIVE -> deductible(card) ? 0 : 1;
			case NOT_STARTED -> 2;
			case EXPIRED -> 3;
		};
	}

	// 묶음 안의 오름차순 기준. 만료 묶음은 늦은 순이라 부호를 뒤집는다
	private static long dateKey(Card card) {
		return switch (card.state()) {
			case ACTIVE -> due(card).toEpochDay();
			case NOT_STARTED -> card.startsOn().toEpochDay();
			case EXPIRED -> -card.endsOn().toEpochDay();
		};
	}

	private static boolean deductible(Card card) {
		return switch (card) {
			case PassCard pass -> pass.remaining() > 0;
			case SubscriptionCard subscription ->
				subscription.currentPeriod() != null && subscription.currentPeriod().remaining() > 0;
		};
	}

	private static LocalDate due(Card card) {
		return switch (card) {
			case PassCard pass -> pass.endsOn();
			case SubscriptionCard subscription ->
				(subscription.currentPeriod() != null) ? subscription.currentPeriod().endsOn() : subscription.endsOn();
		};
	}

}
