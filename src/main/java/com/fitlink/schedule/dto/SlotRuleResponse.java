package com.fitlink.schedule.dto;

import com.fitlink.schedule.model.Weekday;

public record SlotRuleResponse(long id, Weekday weekday, String startTime) {
}
