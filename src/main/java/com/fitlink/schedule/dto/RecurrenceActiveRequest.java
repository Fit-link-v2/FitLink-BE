package com.fitlink.schedule.dto;

import jakarta.validation.constraints.NotNull;

public record RecurrenceActiveRequest(@NotNull(message = "켜기 · 끄기를 true · false로 보낸다") Boolean active) {
}
