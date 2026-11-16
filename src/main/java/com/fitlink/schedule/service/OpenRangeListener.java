package com.fitlink.schedule.service;

import com.fitlink.instructor.InstructorOpenRangeExtendedEvent;
import com.fitlink.schedule.model.GenerationTime;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 강사가 오픈 범위를 늘리면 늘어난 구간의 회차를 바로 만든다. instructor가 이 모듈을 부르면 의존이 순환하므로 이벤트로 받는다.
// 동기 리스너라 설정을 바꾼 트랜잭션 안에서 돈다. 생성이 실패하면 예외가 발행한 쪽으로 올라가 설정 변경도 되돌아간다
@Component
class OpenRangeListener {

	private final SlotGeneration generation;

	OpenRangeListener(SlotGeneration generation) {
		this.generation = generation;
	}

	// 시계를 다시 읽지 않고 요청에서 구한 오늘 · 지금을 쓴다
	@EventListener
	void onExtended(InstructorOpenRangeExtendedEvent event) {
		generation.generateFor(event.instructorId(), new GenerationTime(event.today(), event.now()));
	}

}
