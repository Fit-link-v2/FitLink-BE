package com.fitlink.entitlement.service;

import com.fitlink.instructor.InstructorOpenRangeExtendedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 강사가 오픈 범위를 늘리면 그 강사의 월 정액 주기를 늘어난 범위까지 바로 만든다. 회원이 늘어난 범위의 수업을
// 예약하려면 그 날짜를 덮는 주기 행이 있어야 하기 때문이다. instructor가 이 모듈을 부르면 의존이 순환하므로 이벤트로 받는다.
// 동기 리스너라 설정을 바꾼 트랜잭션 안에서 돈다. 시간표의 같은 리스너와 달리 한 건의 실패가 설정 변경을 되돌리지 않는다.
// 시간표 모듈에 같은 이름의 리스너가 있어 빈 이름이 겹치지 않도록 이름을 달리 했다
@Component
class PeriodOpenRangeListener {

	private final PeriodGeneration generation;

	PeriodOpenRangeListener(PeriodGeneration generation) {
		this.generation = generation;
	}

	// 시계를 다시 읽지 않고 요청에서 구한 오늘을 쓴다. 자정 경계에서 회차와 주기의 날짜가 어긋나지 않게 하려는 것이다
	@EventListener
	void onExtended(InstructorOpenRangeExtendedEvent event) {
		generation.generateFor(event.instructorId(), event.today());
	}

}
