package com.fitlink.schedule.dto;

import com.fitlink.schedule.model.Slot;

public record SlotSummaryResponse(long id, String startsAt, int capacity, int taken, String canceledAt) {

	public static SlotSummaryResponse of(Slot slot) {
		return new SlotSummaryResponse(slot.id(), ApiFormats.seoul(slot.startsAt()), slot.capacity(), slot.taken(),
				ApiFormats.seoul(slot.canceledAt()));
	}

}
