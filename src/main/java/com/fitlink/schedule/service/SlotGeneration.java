package com.fitlink.schedule.service;

import com.fitlink.schedule.model.GenerationTime;
import com.fitlink.schedule.repository.SlotRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotGeneration {

	private final SlotRepository slots;

	SlotGeneration(SlotRepository slots) {
		this.slots = slots;
	}

	// 그 강사의 켜진 규칙으로 오픈 범위 안의 회차를 만들고 새로 만든 수를 돌려준다
	public int generateFor(long instructorId, GenerationTime time) {
		return slots.generate(instructorId, time.today(), time.now());
	}

	public int generateAll(GenerationTime time) {
		return slots.generate(null, time.today(), time.now());
	}

	// 규칙을 만들거나 다시 켠 뒤에 부른다. 규칙을 여러 개 함께 켜면 모두 이어받은 뒤에 회차를 한 번만 만든다.
	// 먼저 만들면 아직 이어받지 않은 규칙의 회차가 그 날에 생겨 이어받기가 회차 유니크에 걸린다
	@Transactional
	public void takeOverAndGenerate(List<Long> recurrenceIds, long instructorId, GenerationTime time) {
		recurrenceIds.forEach(recurrenceId -> slots.takeOver(recurrenceId, instructorId, time.today()));
		slots.generate(instructorId, time.today(), time.now());
	}

}
