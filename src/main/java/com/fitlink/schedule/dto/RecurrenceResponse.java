package com.fitlink.schedule.dto;

import com.fitlink.schedule.model.Recurrence;
import com.fitlink.schedule.model.Weekday;

public record RecurrenceResponse(long id, Weekday weekday, String startTime, int capacity, boolean active) {

	public static RecurrenceResponse of(Recurrence recurrence) {
		return new RecurrenceResponse(recurrence.id(), recurrence.weekday(), ApiFormats.hhmm(recurrence.startTime()),
				recurrence.capacity(), recurrence.active());
	}

}
