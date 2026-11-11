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
		List<Weekday> taken = recurrences
			.findActiveWeekdays(instructorId, weekdays.stream().map(Weekday::dow).toList(), startTime)
			.stream()
			.map(Weekday::ofDow)
			.sorted()
			.toList();
		if (!taken.isEmpty()) {
			throw new RecurrenceDuplicateException(taken);
		}
		List<Recurrence> created = weekdays.stream()
			.map(weekday -> new Recurrence(recurrences.insert(instructorId, weekday.dow(), startTime, capacity), weekday,
					startTime, capacity, true))
			.toList();
		generation.takeOverAndGenerate(created.stream().map(Recurrence::id).toList(), instructorId,
				GenerationTime.at(clock));
		return created;
	}

	// 월요일부터 일요일까지, 같은 요일은 시각 순
	public List<Recurrence> list(long instructorId) {
		return recurrences.findAll(instructorId);
	}

}
