package com.fitlink.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record SlotChangeRequest(@NotNull(message = "시작 시각을 HH:mm으로 보낸다") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
		LocalDate date) {
}
