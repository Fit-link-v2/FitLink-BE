package com.fitlink.schedule.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitlink.common.exception.FitlinkException;
import java.util.List;
import org.junit.jupiter.api.Test;

// 동시에 들어온 요청이 앞의 확인을 지나 DB 유니크에 걸릴 때 쓸 에러 코드를 본다. 경합을 재현하기 어려워 대응표를 직접 부른다
class ScheduleConstraintErrorsTests {

	ScheduleConstraintErrors errors = new ScheduleConstraintErrors();

	@Test
	void 켜진_규칙_유니크_위반은_RECURRENCE_DUPLICATE이고_겹친_요일은_비운다() {
		FitlinkException e = errors.find("recurrence_slot_uidx").orElseThrow();

		assertThat(e.errorCode()).isEqualTo(ScheduleErrorCode.RECURRENCE_DUPLICATE);
		assertThat(e.details()).containsEntry("weekdays", List.of());
	}

	@Test
	void 같은_시각_수업_유니크_위반은_SLOT_TIME_TAKEN() {
		FitlinkException e = errors.find("class_slot_time_uidx").orElseThrow();

		assertThat(e.errorCode()).isEqualTo(ScheduleErrorCode.SLOT_TIME_TAKEN);
	}

	@Test
	void 대응표에_없는_제약은_비워_돌려준다() {
		assertThat(errors.find("class_slot_occurrence_uk")).isEmpty();
	}

}
