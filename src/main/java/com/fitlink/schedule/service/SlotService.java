package com.fitlink.schedule.service;

import com.fitlink.common.exception.CommonErrorCode;
import com.fitlink.common.exception.FitlinkException;
import com.fitlink.common.exception.InvalidRequestException;
import com.fitlink.instructor.InstructorFacade;
import com.fitlink.schedule.exception.ScheduleErrorCode;
import com.fitlink.schedule.model.ScheduleZone;
import com.fitlink.schedule.model.Slot;
import com.fitlink.schedule.model.UpcomingSlots;
import com.fitlink.schedule.repository.SlotRepository;
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

	// untilApplied가 읽고 쓰기를 시도하는 횟수. 첫 시도를 포함해 3번, 다시 읽기는 2번이다
	private static final int ATTEMPTS = 3;

	private final SlotRepository slots;

	private final InstructorFacade instructors;

	private final Clock clock;

	SlotService(SlotRepository slots, InstructorFacade instructors, Clock clock) {
		this.slots = slots;
		this.instructors = instructors;
		this.clock = clock;
	}

	// 시작 시각이 지금 이후인 슬롯 전부. 휴강한 슬롯과 오픈 범위를 줄여 범위 밖에 남은 슬롯도 넣는다.
	// 예약이 걸려 있을 수 있어 강사 눈에서 사라지면 안 되기 때문이다. 이미 시작한 수업은 넣지 않는다.
	// 오픈 범위와 슬롯을 두 번 읽으므로 같은 시점을 보도록 REPEATABLE READ로 묶는다
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public UpcomingSlots upcoming(long instructorId) {
		return new UpcomingSlots(instructors.openRangeDays(instructorId),
				slots.findUpcoming(instructorId, OffsetDateTime.now(clock)));
	}

	// 상세는 지난 슬롯도 준다
	@Transactional(readOnly = true)
	public Slot detail(long instructorId, long id) {
		return owned(instructorId, id);
	}

	// 강사가 직접 여는 일회성 수업. 회차 생성은 돌지 않는다.
	// 판정 순서: 날짜가 오픈 범위 밖 · 시작 시각이 지금 이전(400) → 같은 시각 수업(409, DB 유니크)
	@Transactional
	public Slot createOneOff(long instructorId, LocalDate date, LocalTime startTime, int capacity) {
		OffsetDateTime now = now();
		requireInOpenRange(instructorId, date, now);
		OffsetDateTime startsAt = requireAfterNow(date, startTime, now);
		return owned(instructorId, slots.insertOneOff(instructorId, date, startsAt, capacity));
	}

	// 그 슬롯의 시각을 바꾼다. 반복 회차는 같은 날 안에서만, 일회성 수업은 date로 다른 날로도 옮긴다.
	// 규칙과 다른 주 회차는 그대로다. 판정 순서: 404 → 휴강 → 시작함 →
	// 400(반복 회차에 date · 새 날짜가 오픈 범위 밖 · 새 시각이 지금 이전) → 예약 있음 → 같은 시각 수업(409, DB 유니크)
	@Transactional
	public Slot change(long instructorId, long id, LocalTime startTime, LocalDate date) {
		OffsetDateTime now = now();
		return untilApplied(instructorId, id, "시각을 바꾸지", slot -> {
			rejectClosed(slot, now);
			LocalDate day = newDay(instructorId, slot, date, now);
			OffsetDateTime startsAt = requireAfterNow(day, startTime, now);
			if (slot.taken() > 0) {
				throw new FitlinkException(ScheduleErrorCode.SLOT_HAS_BOOKINGS);
			}
			return slots.changeTime(instructorId, id, day, startsAt, now);
		});
	}

	// 행을 남기고 canceled_at을 채운다. 예약이 있어도 휴강하고, 되돌리는 길은 없다.
	// 판정 순서: 404 → 이미 휴강이면 처음 값 그대로 → 시작함
	@Transactional
	public Slot cancel(long instructorId, long id) {
		OffsetDateTime now = now();
		return untilApplied(instructorId, id, "휴강하지", slot -> {
			if (slot.canceled()) {
				return true;
			}
			rejectClosed(slot, now);
			return slots.cancel(instructorId, id, now);
		});
	}

	// 읽고 판정한 뒤 조건부 UPDATE로 쓴다. 그 사이 다른 요청이 끼어들어 0행이면 다시 읽어 같은 순서로 판정한다.
	// 상태가 계속 바뀌는 경우를 끝없이 따라가지 않도록 ATTEMPTS번까지만 하고, 그래도 못 쓰면 500이다.
	private Slot untilApplied(long instructorId, long id, String action, Attempt attempt) {
		for (int i = 0; i < ATTEMPTS; i++) {
			if (attempt.apply(owned(instructorId, id))) {
				return owned(instructorId, id);
			}
		}
		throw new IllegalStateException("슬롯 " + id + "의 상태가 계속 바뀌어 " + action + " 못했다");
	}

	// 수업이 열릴 날. date가 없거나 지금 날짜와 같으면 날짜를 옮기지 않으므로 오픈 범위를 보지 않는다
	// (범위를 줄여 범위 밖에 남은 수업도 시각은 고칠 수 있다). FE가 수정 화면에서 date를 늘 보내도 되도록
	// 반복 회차도 같은 날짜는 받는다. 다른 날짜는 반복 회차가 규칙의 요일을 따라야 해 받지 않는다
	private LocalDate newDay(long instructorId, Slot slot, LocalDate date, OffsetDateTime now) {
		if (date == null || date.equals(slot.occursOn())) {
			return slot.occursOn();
		}
		if (slot.recurrence() != null) {
			throw new InvalidRequestException("date", "반복 회차는 날짜를 바꿀 수 없다");
		}
		requireInOpenRange(instructorId, date, now);
		return date;
	}

	// 오픈 범위는 오늘부터 N일이고 끝 날짜는 넣지 않는다. 회차 생성과 같은 범위다
	private void requireInOpenRange(long instructorId, LocalDate date, OffsetDateTime now) {
		LocalDate today = now.atZoneSameInstant(ScheduleZone.SEOUL).toLocalDate();
		if (date.isBefore(today) || !date.isBefore(today.plusDays(instructors.openRangeDays(instructorId)))) {
			throw new InvalidRequestException("date", "오픈 범위 안의 날짜를 고른다");
		}
	}

	// 읽은 슬롯으로 판정하고 조건부 UPDATE를 한다. 판정에 걸리면 예외를 던지고,
	// 썼거나 쓸 필요가 없으면 true, 그 사이 상태가 바뀌어 0행이면 false다
	@FunctionalInterface
	private interface Attempt {

		boolean apply(Slot slot);

	}

	// 날짜 + 시각을 서울 시각으로 읽는다. 지금이면 이미 시작한 수업이라 받지 않는다
	private static OffsetDateTime requireAfterNow(LocalDate day, LocalTime startTime, OffsetDateTime now) {
		OffsetDateTime startsAt = day.atTime(startTime).atZone(ScheduleZone.SEOUL).toOffsetDateTime();
		if (!startsAt.isAfter(now)) {
			throw new InvalidRequestException("startTime", "지금 이후 시각을 고른다");
		}
		return startsAt;
	}

	// DB는 마이크로초까지 저장한다. 나노초가 남은 채로 넘기면 드라이버가 반올림해, 자바의 판정과 UPDATE의 WHERE가
	// 경계에서 다른 답을 낸다(자바는 시작 전, SQL은 시작함 → 0행 반복). 한 번 잘라 양쪽이 같은 값을 쓰게 한다
	private OffsetDateTime now() {
		return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

	private Slot owned(long instructorId, long id) {
		return slots.find(instructorId, id).orElseThrow(() -> new FitlinkException(CommonErrorCode.NOT_FOUND));
	}

	// 휴강했거나 시작한 수업은 더 바꿀 수 없다
	private static void rejectClosed(Slot slot, OffsetDateTime now) {
		if (slot.canceled()) {
			throw new FitlinkException(ScheduleErrorCode.SLOT_CANCELED);
		}
		if (slot.startedAt(now)) {
			throw new FitlinkException(ScheduleErrorCode.CLASS_STARTED);
		}
	}

}
