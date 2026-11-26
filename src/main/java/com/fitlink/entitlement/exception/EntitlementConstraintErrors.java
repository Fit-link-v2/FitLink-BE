package com.fitlink.entitlement.exception;

import com.fitlink.common.exception.ConstraintErrors;
import com.fitlink.common.exception.FitlinkException;
import java.util.Optional;
import org.springframework.stereotype.Component;

// 수강권 테이블의 제약 위반을 에러 코드로 바꾼다. 등록은 회원 행을 잠그고 검사하므로 정상이라면 걸리지 않는다.
// 그래도 걸리면 다른 종류의 종료일을 다시 읽을 수 없으므로, 등록할 수 있는 날짜 없이 기간이 겹친다고만 답한다
@Component
class EntitlementConstraintErrors implements ConstraintErrors {

	@Override
	public Optional<FitlinkException> find(String constraint) {
		return switch (constraint) {
			case "entitlement_kind_no_overlap" ->
				Optional.of(new FitlinkException(EntitlementErrorCode.ENTITLEMENT_PERIOD_OVERLAP));
			default -> Optional.empty();
		};
	}

}
