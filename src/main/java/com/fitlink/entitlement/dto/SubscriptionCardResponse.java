package com.fitlink.entitlement.dto;

import com.fitlink.entitlement.model.CurrentPeriod;
import com.fitlink.entitlement.model.SubscriptionCard;
import java.time.LocalDate;

// 월 정액 카드 { kind: "SUBSCRIPTION", id, limit: { per, count }, startsOn, endsOn, state, currentPeriod }.
// limit은 등록 요청과 같은 모양이다. currentPeriod가 없으면 null로 준다
public record SubscriptionCardResponse(String kind, long id, Limit limit, LocalDate startsOn, LocalDate endsOn,
		String state, Period currentPeriod) implements EntitlementCardResponse {

	public static SubscriptionCardResponse of(SubscriptionCard card) {
		CurrentPeriod current = card.currentPeriod();
		Period period = (current == null) ? null : new Period(current.entitlementId(), current.startsOn(),
				current.endsOn(), current.maxCount(), current.remaining());
		return new SubscriptionCardResponse("SUBSCRIPTION", card.id(), new Limit(card.per().name(), card.count()),
				card.startsOn(), card.endsOn(), card.state().name(), period);
	}

	public record Limit(String per, int count) {
	}

	public record Period(long entitlementId, LocalDate startsOn, LocalDate endsOn, int maxCount, int remaining) {
	}

}
