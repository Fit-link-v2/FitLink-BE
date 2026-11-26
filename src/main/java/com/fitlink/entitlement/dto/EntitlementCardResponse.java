package com.fitlink.entitlement.dto;

import com.fitlink.entitlement.model.Card;
import com.fitlink.entitlement.model.PassCard;
import com.fitlink.entitlement.model.SubscriptionCard;

// 수강권 카드 응답. 종류마다 record 하나이고 kind로 구분한다
public sealed interface EntitlementCardResponse permits PassCardResponse, SubscriptionCardResponse {

	static EntitlementCardResponse of(Card card) {
		return switch (card) {
			case PassCard pass -> PassCardResponse.of(pass);
			case SubscriptionCard subscription -> SubscriptionCardResponse.of(subscription);
		};
	}

}
