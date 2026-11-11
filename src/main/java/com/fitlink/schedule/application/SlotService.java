package com.fitlink.schedule.application;

import com.fitlink.instructor.application.SettingService;
import com.fitlink.schedule.infra.SlotRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotService {

	private final SlotRepository slots;

	private final SettingService settings;

	private final Clock clock;

	SlotService(SlotRepository slots, SettingService settings, Clock clock) {
		this.slots = slots;
		this.settings = settings;
		this.clock = clock;
	}

	// 시작 시각이 지금 이후인 슬롯 전부. 휴강한 슬롯과 오픈 범위를 줄여 범위 밖에 남은 슬롯도 넣는다.
	// 예약이 걸려 있을 수 있어 강사 눈에서 사라지면 안 되기 때문이다. 이미 시작한 수업은 넣지 않는다.
	// 오픈 범위와 슬롯을 두 번 읽으므로 같은 시점을 보도록 REPEATABLE READ로 묶는다
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public UpcomingSlots upcoming(long instructorId) {
		return new UpcomingSlots(settings.openRangeDays(instructorId),
				slots.findUpcoming(instructorId, OffsetDateTime.now(clock)));
	}

	// 상세는 지난 슬롯도 준다
	@Transactional(readOnly = true)
	public Slot detail(long instructorId, long id) {
		return slots.find(instructorId, id).orElseThrow(NotFoundException::new);
	}

}
