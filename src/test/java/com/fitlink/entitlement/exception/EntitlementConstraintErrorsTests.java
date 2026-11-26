package com.fitlink.entitlement.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitlink.common.exception.FitlinkException;
import org.junit.jupiter.api.Test;

// 등록 검사를 지나 DB의 종류 겹침 제약에 걸릴 때 쓸 에러 코드를 본다. 등록은 회원 행을 잠가 이 경합이 생기지 않으므로 대응표를 직접 부른다
class EntitlementConstraintErrorsTests {

	EntitlementConstraintErrors errors = new EntitlementConstraintErrors();

	@Test
	void 종류_겹침_제약_위반은_ENTITLEMENT_PERIOD_OVERLAP() {
		FitlinkException e = errors.find("entitlement_kind_no_overlap").orElseThrow();

		assertThat(e.errorCode()).isEqualTo(EntitlementErrorCode.ENTITLEMENT_PERIOD_OVERLAP);
	}

	@Test
	void 대응표에_없는_제약은_비워_돌려준다() {
		assertThat(errors.find("entitlement_used_range")).isEmpty();
	}

}
