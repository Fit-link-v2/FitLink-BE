package com.fitlink.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

// date는 일회성 수업만 받는다. 반복 회차에 보내면 400
public record SlotChangeRequest(@NotNull(message = "시작 시각을 HH:mm으로 보낸다") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
		LocalDate date) {
}
