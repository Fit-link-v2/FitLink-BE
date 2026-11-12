package com.fitlink.schedule.application;

import com.fitlink.schedule.infra.RecurrenceRepository;
import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurrenceService {

	private final RecurrenceRepository recurrences;

	private final SlotGeneration generation;

	private final Clock clock;

	RecurrenceService(RecurrenceRepository recurrences, SlotGeneration generation, Clock clock) {
		this.recurrences = recurrences;
		this.generation = generation;
		this.clock = clock;
	}

	// 요일마다 규칙 1개를 한 트랜잭션에서 만들고, 오픈 범위 안의 회차까지 만든다.
	// 겹치는 요일이 하나라도 있으면 아무것도 저장하지 않는다. 응답은 목록과 같은 월요일부터의 순서다
	@Transactional
	public List<Recurrence> register(long instructorId, List<Weekday> requested, LocalTime startTime, int capacity) {
		List<Weekday> weekdays = requested.stream().distinct().sorted().toList();
		rejectActiveOverlap(instructorId, weekdays, startTime);
		List<Recurrence> created = weekdays.stream()
			.map(weekday -> new Recurrence(recurrences.insert(instructorId, weekday.dow(), startTime, capacity), weekday,
					startTime, capacity, true))
			.toList();
		fillOpenRange(instructorId, created);
		return created;
	}

	// 끄면 이미 만든 회차는 그대로 두고 이후 생성만 멈춘다. 다시 켜면 꺼진 같은 요일 · 시각 규칙의 회차를 이어받고
	// 오픈 범위 안의 회차를 만든다. 이미 그 상태면 아무것도 하지 않는다
	@Transactional
	public Recurrence setActive(long instructorId, long id, boolean active) {
		Recurrence current = recurrences.findForUpdate(instructorId, id).orElseThrow(NotFoundException::new);
		if (current.active() == active) {
			return current;
		}
		if (active) {
			rejectActiveOverlap(instructorId, List.of(current.weekday()), current.startTime());
		}
		recurrences.updateActive(id, active);
		Recurrence changed = new Recurrence(current.id(), current.weekday(), current.startTime(), current.capacity(),
				active);
		if (active) {
			fillOpenRange(instructorId, List.of(changed));
		}
		return changed;
	}

	// 월요일부터 일요일까지, 같은 요일은 시각 순
	public List<Recurrence> list(long instructorId) {
		return recurrences.findAll(instructorId);
	}

	// 같은 요일 · 시각의 켜진 규칙이 있으면 겹친 요일을 알려 주고 멈춘다
	private void rejectActiveOverlap(long instructorId, List<Weekday> weekdays, LocalTime startTime) {
		List<Weekday> taken = recurrences
			.findActiveWeekdays(instructorId, weekdays.stream().map(Weekday::dow).toList(), startTime)
			.stream()
			.map(Weekday::ofDow)
			.sorted()
			.toList();
		if (!taken.isEmpty()) {
			throw new RecurrenceDuplicateException(taken);
		}
	}

	// 켜진 규칙이 꺼진 같은 요일 · 시각 규칙의 남은 회차를 이어받은 뒤 오픈 범위 안의 회차를 만든다
	private void fillOpenRange(long instructorId, List<Recurrence> turnedOn) {
		generation.takeOverAndGenerate(turnedOn.stream().map(Recurrence::id).toList(), instructorId,
				GenerationTime.at(clock));
	}

}
