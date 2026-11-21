package com.fitlink.entitlement.service;

import com.fitlink.common.exception.FitlinkException;
import com.fitlink.common.exception.InvalidRequestException;
import com.fitlink.entitlement.exception.EntitlementErrorCode;
import com.fitlink.entitlement.model.CardState;
import com.fitlink.entitlement.model.PassCard;
import com.fitlink.entitlement.model.PassRegistration;
import com.fitlink.entitlement.repository.EntitlementRepository;
import com.fitlink.membership.MembershipFacade;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementService {

	private final EntitlementRepository entitlements;

	private final MembershipFacade memberships;

	private final Clock clock;

	EntitlementService(EntitlementRepository entitlements, MembershipFacade memberships, Clock clock) {
		this.entitlements = entitlements;
		this.memberships = memberships;
		this.clock = clock;
	}

	// 판정 순서는 입력(400) → 회원(404) → 수강 종료(422)다. 회원 행을 잠그고 시작해 같은 회원의 등록이 동시에 와도 차례로 돈다.
	// 지난 시작일은 받는다. 이미 진행 중인 수강권을 옮겨 적는 경우다
	@Transactional
	public PassCard register(long instructorId, long memberId, PassRegistration pass) {
		LocalDate today = LocalDate.now(clock);
		if (pass.endsOn().isBefore(today)) {
			throw new InvalidRequestException("endsOn", "종료일은 오늘 이후다");
		}
		if (!memberships.lockActive(instructorId, memberId)) {
			throw new FitlinkException(EntitlementErrorCode.MEMBER_ENDED);
		}
		long id = entitlements.insertPass(memberId, pass);
		return new PassCard(id, pass.startsOn(), pass.endsOn(), pass.maxCount(), pass.maxCount(),
				CardState.of(pass.startsOn(), pass.endsOn(), today));
	}

}
