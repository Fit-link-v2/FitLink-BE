package com.fitlink.entitlement.service;

import com.fitlink.common.exception.FitlinkException;
import com.fitlink.common.exception.InvalidRequestException;
import com.fitlink.entitlement.exception.EntitlementErrorCode;
import com.fitlink.entitlement.model.Card;
import com.fitlink.entitlement.model.OtherKind;
import com.fitlink.entitlement.model.PassCard;
import com.fitlink.entitlement.model.PassRegistration;
import com.fitlink.entitlement.model.Registration;
import com.fitlink.entitlement.model.SubscriptionCard;
import com.fitlink.entitlement.model.SubscriptionRegistration;
import com.fitlink.entitlement.repository.EntitlementRepository;
import com.fitlink.entitlement.repository.SubscriptionRepository;
import com.fitlink.membership.MembershipFacade;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementService {

	private final EntitlementRepository entitlements;

	private final SubscriptionRepository subscriptions;

	private final PeriodGeneration periods;

	private final CardReader cards;

	private final MembershipFacade memberships;

	private final Clock clock;

	EntitlementService(EntitlementRepository entitlements, SubscriptionRepository subscriptions,
			PeriodGeneration periods, CardReader cards, MembershipFacade memberships, Clock clock) {
		this.entitlements = entitlements;
		this.subscriptions = subscriptions;
		this.periods = periods;
		this.cards = cards;
		this.memberships = memberships;
		this.clock = clock;
	}

	// 판정 순서는 입력(400) → 회원(404) → 수강 종료(422) → 다른 종류(422)다.
	// 회원 행을 잠그고 검사해, 같은 회원에게 다른 종류 등록 두 개가 동시에 와도 늦은 쪽은 먼저 저장된 행을 보고 검사한다.
	// 지난 시작일은 받는다. 이미 진행 중인 수강권을 옮겨 적는 경우다
	@Transactional
	public Card register(long instructorId, long memberId, Registration registration) {
		LocalDate today = LocalDate.now(clock);
		if (registration.endsOn().isBefore(today)) {
			// 월 정액은 종료일을 받지 않으므로 시작일 문제로 알려 준다
			String field = (registration instanceof PassRegistration) ? "endsOn" : "startsOn";
			throw new InvalidRequestException(field, "종료일은 오늘 이후다");
		}
		if (!memberships.lockActive(instructorId, memberId)) {
			throw new FitlinkException(EntitlementErrorCode.MEMBER_ENDED);
		}
		rejectOtherKind(otherKind(memberId, registration, today));
		return switch (registration) {
			case PassRegistration pass -> registerPass(instructorId, memberId, pass, today);
			case SubscriptionRegistration subscription ->
				registerSubscription(instructorId, memberId, subscription, today);
		};
	}

	// 같은 종류끼리는 보지 않는다. 겹쳐도 된다
	private OtherKind otherKind(long memberId, Registration registration, LocalDate today) {
		return switch (registration) {
			case PassRegistration pass -> subscriptions.against(memberId, pass.startsOn(), pass.endsOn(), today);
			case SubscriptionRegistration subscription ->
				entitlements.passesAgainst(memberId, subscription.startsOn(), subscription.endsOn(), today);
		};
	}

	// 만료되지 않은 다른 종류가 먼저다. 그 수강권들이 모두 끝난 다음 날부터 등록할 수 있다
	private static void rejectOtherKind(OtherKind other) {
		if (other.activeUntil() != null) {
			throw new FitlinkException(EntitlementErrorCode.ENTITLEMENT_OTHER_KIND_ACTIVE,
					Map.of("availableFrom", other.activeUntil().plusDays(1).toString()));
		}
		if (other.overlaps()) {
			throw new FitlinkException(EntitlementErrorCode.ENTITLEMENT_PERIOD_OVERLAP);
		}
	}

	// 응답 카드는 목록과 같은 계산으로 방금 저장한 행을 다시 읽어 만든다
	private PassCard registerPass(long instructorId, long memberId, PassRegistration pass, LocalDate today) {
		long id = entitlements.insertPass(memberId, pass);
		return cards.card(instructorId, memberId, PassCard.class, id, today);
	}

	// 오픈 범위 안의 주기를 같은 트랜잭션에서 바로 만든다. 회원이 그 범위의 수업을 예약하려면 주기 행이 있어야 한다
	private SubscriptionCard registerSubscription(long instructorId, long memberId,
			SubscriptionRegistration subscription, LocalDate today) {
		long id = subscriptions.insert(memberId, subscription);
		periods.generate(id, today);
		return cards.card(instructorId, memberId, SubscriptionCard.class, id, today);
	}

}
