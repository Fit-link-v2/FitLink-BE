package com.fitlink.entitlement.dto;

import com.fitlink.entitlement.model.PassCard;
import java.time.LocalDate;

// 횟수권 카드 { kind: "PASS", id, startsOn, endsOn, maxCount, remaining, state }. 회원 화면 카드와 같은 모양이다
public record PassCardResponse(String kind, long id, LocalDate startsOn, LocalDate endsOn, int maxCount,
		int remaining, String state) implements EntitlementCardResponse {

	public static PassCardResponse of(PassCard card) {
		return new PassCardResponse("PASS", card.id(), card.startsOn(), card.endsOn(), card.maxCount(),
				card.remaining(), card.state().name());
	}

}
