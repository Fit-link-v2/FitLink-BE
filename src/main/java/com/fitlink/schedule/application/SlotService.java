package com.fitlink.schedule.application;

import com.fitlink.instructor.application.SettingService;
import com.fitlink.schedule.infra.SlotRepository;
import com.fitlink.schedule.application.SlotRejectedException.Reason;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotService {

	// 조건부 UPDATE가 0행이면 다시 읽어 판정한다. 그 사이 상태가 계속 바뀌는 경우를 끝없이 따라가지 않는다
	private static final int ATTEMPTS = 3;

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
		return owned(instructorId, id);
	}

	// 그 슬롯의 시각만 같은 날 안에서 바꾼다. 규칙과 다른 주 회차는 그대로다.
	// 판정 순서: 404 → 휴강 → 시작함 → 날짜를 보냄 · 새 시각이 지금 이전(400) → 예약 있음 → 같은 시각 수업(409, DB 유니크)
	@Transactional
	public Slot changeTime(long instructorId, long id, LocalTime startTime, LocalDate date) {
		OffsetDateTime now = now();
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			Slot slot = owned(instructorId, id);
			rejectClosed(slot, now);
			if (date != null) {
				throw new InvalidRequestException("date", "날짜는 바꿀 수 없다");
			}
			OffsetDateTime startsAt = slot.occursOn().atTime(startTime).atZone(ScheduleZone.SEOUL).toOffsetDateTime();
			if (!startsAt.isAfter(now)) {
				throw new InvalidRequestException("startTime", "지금 이후 시각으로 옮긴다");
			}
			if (slot.taken() > 0) {
				throw new SlotRejectedException(Reason.SLOT_HAS_BOOKINGS);
			}
			if (slots.changeTime(instructorId, id, startsAt, now)) {
				return owned(instructorId, id);
			}
		}
		throw new IllegalStateException("슬롯 " + id + "의 상태가 계속 바뀌어 시각을 바꾸지 못했다");
	}

	// 행을 남기고 canceled_at을 채운다. 예약이 있어도 휴강하고, 되돌리는 길은 없다.
	// 판정 순서: 404 → 이미 휴강이면 처음 값 그대로 → 시작함
	@Transactional
	public Slot cancel(long instructorId, long id) {
		OffsetDateTime now = now();
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			Slot slot = owned(instructorId, id);
			if (slot.canceled()) {
				return slot;
			}
			rejectClosed(slot, now);
			if (slots.cancel(instructorId, id, now)) {
				return owned(instructorId, id);
			}
		}
		throw new IllegalStateException("슬롯 " + id + "의 상태가 계속 바뀌어 휴강하지 못했다");
	}

	// DB는 마이크로초까지 저장한다. 나노초가 남은 채로 넘기면 드라이버가 반올림해, 자바의 판정과 UPDATE의 WHERE가
	// 경계에서 다른 답을 낸다(자바는 시작 전, SQL은 시작함 → 0행 반복). 한 번 잘라 양쪽이 같은 값을 쓰게 한다
	private OffsetDateTime now() {
		return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

	private Slot owned(long instructorId, long id) {
		return slots.find(instructorId, id).orElseThrow(NotFoundException::new);
	}

	// 휴강했거나 시작한 수업은 더 바꿀 수 없다
	private static void rejectClosed(Slot slot, OffsetDateTime now) {
		if (slot.canceled()) {
			throw new SlotRejectedException(Reason.SLOT_CANCELED);
		}
		if (slot.startedAt(now)) {
			throw new SlotRejectedException(Reason.CLASS_STARTED);
		}
	}

}
