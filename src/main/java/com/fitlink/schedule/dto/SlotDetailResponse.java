package com.fitlink.schedule.dto;

import com.fitlink.schedule.model.Slot;

public record SlotDetailResponse(long id, String startsAt, int capacity, int taken, String canceledAt,
		SlotRuleResponse recurrence) {

	public static SlotDetailResponse of(Slot slot) {
		Slot.Rule rule = slot.recurrence();
		return new SlotDetailResponse(slot.id(), ApiFormats.seoul(slot.startsAt()), slot.capacity(), slot.taken(),
				ApiFormats.seoul(slot.canceledAt()),
				rule == null ? null : new SlotRuleResponse(rule.id(), rule.weekday(), ApiFormats.hhmm(rule.startTime())));
	}

}
