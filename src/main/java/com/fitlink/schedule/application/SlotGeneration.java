package com.fitlink.schedule.application;

import com.fitlink.schedule.infra.SlotRepository;
import org.springframework.stereotype.Service;

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

}
