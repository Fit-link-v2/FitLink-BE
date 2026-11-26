package com.fitlink.entitlement;

import com.fitlink.entitlement.EntitlementCardInfo.Pass;
import com.fitlink.entitlement.EntitlementCardInfo.Period;
import com.fitlink.entitlement.EntitlementCardInfo.Subscription;
import com.fitlink.entitlement.model.Card;
import com.fitlink.entitlement.model.CurrentPeriod;
import com.fitlink.entitlement.model.PassCard;
import com.fitlink.entitlement.model.SubscriptionCard;
import com.fitlink.entitlement.service.CardReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// entitlement 모듈이 다른 모듈에 여는 창구. Info로 바꾸는 것 말고는 로직을 두지 않고 service에 넘긴다
@Component
public class EntitlementFacade {

	private final CardReader cards;

	EntitlementFacade(CardReader cards) {
		this.cards = cards;
	}

	// 회원별 카드. 각 목록은 다음에 차감될 순서이고 맨 앞이 다음에 차감될 수강권이다. 만료된 카드도 맨 뒤에 들어 있다.
	// 카드가 없는 회원과 다른 강사의 회원은 결과에 없다. 회원 수와 상관없이 쿼리는 한 번이다
	public Map<Long, List<EntitlementCardInfo>> cards(long instructorId, Collection<Long> memberIds) {
		return cards.cards(instructorId, memberIds)
			.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey,
					entry -> entry.getValue().stream().map(EntitlementFacade::info).toList()));
	}

	private static EntitlementCardInfo info(Card card) {
		return switch (card) {
			case PassCard pass -> new Pass(pass.id(), pass.startsOn(), pass.endsOn(), pass.maxCount(),
					pass.remaining(), pass.state().name());
			case SubscriptionCard subscription -> new Subscription(subscription.id(), subscription.per().name(),
					subscription.count(), subscription.startsOn(), subscription.endsOn(), subscription.state().name(),
					period(subscription.currentPeriod()));
		};
	}

	private static Period period(CurrentPeriod current) {
		return (current == null) ? null : new Period(current.entitlementId(), current.startsOn(), current.endsOn(),
				current.maxCount(), current.remaining());
	}

}
