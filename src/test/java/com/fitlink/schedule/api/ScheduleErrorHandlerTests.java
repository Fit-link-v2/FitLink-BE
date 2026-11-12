package com.fitlink.schedule.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

// 동시에 들어온 요청이 앞의 확인을 지나 DB 유니크에 걸릴 때를 응답 코드로 바꾸는지 본다. 경합을 재현하기 어려워 처리기를 직접 부른다
class ScheduleErrorHandlerTests {

	ScheduleErrorHandler handler = new ScheduleErrorHandler();

	@Test
	void 켜진_규칙_유니크_위반은_409_RECURRENCE_DUPLICATE() {
		var response = handler.duplicate(violation("recurrence_slot_uidx"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().code()).isEqualTo("RECURRENCE_DUPLICATE");
		assertThat(response.getBody().weekdays()).isEmpty();
	}

	@Test
	void 같은_시각_수업_유니크_위반은_409_SLOT_TIME_TAKEN() {
		var response = handler.duplicate(violation("class_slot_time_uidx"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().code()).isEqualTo("SLOT_TIME_TAKEN");
	}

	@Test
	void 대응표에_없는_제약은_그대로_던져_500이_된다() {
		DuplicateKeyException unknown = violation("class_slot_occurrence_uk");

		assertThatThrownBy(() -> handler.duplicate(unknown)).isSameAs(unknown);
	}

	private static DuplicateKeyException violation(String constraint) {
		return new DuplicateKeyException(
				"ERROR: duplicate key value violates unique constraint \"" + constraint + "\"");
	}

}
