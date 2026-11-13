package com.fitlink.schedule.exception;

import com.fitlink.common.exception.ConstraintErrors;
import com.fitlink.common.exception.FitlinkException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

// 시간표 테이블의 제약 위반을 에러 코드로 바꾼다. 서비스의 앞선 확인을 동시에 지나 DB에 걸린 경우라,
// 앞선 확인에서 걸렸을 때와 같은 코드로 응답한다
@Component
class ScheduleConstraintErrors implements ConstraintErrors {

	@Override
	public Optional<FitlinkException> find(String constraint) {
		return switch (constraint) {
			// 겹친 요일을 다시 읽을 수 없어 비워 둔다
			case "recurrence_slot_uidx" -> Optional
				.of(new FitlinkException(ScheduleErrorCode.RECURRENCE_DUPLICATE, Map.of("weekdays", List.of())));
			case "class_slot_time_uidx" -> Optional.of(new FitlinkException(ScheduleErrorCode.SLOT_TIME_TAKEN));
			default -> Optional.empty();
		};
	}

}
