package com.fitlink.schedule.dto;

import com.fitlink.schedule.model.Weekday;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;

public record RecurrenceRegisterRequest(
		@NotEmpty(message = "요일을 하나 이상 고른다") List<@NotNull(message = "요일을 하나 이상 고른다") Weekday> weekdays,
		@NotNull(message = "시작 시각을 HH:mm으로 보낸다") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@NotNull(message = "정원을 보낸다") @Min(value = 1, message = "정원은 1 이상 50 이하다") @Max(value = 50,
				message = "정원은 1 이상 50 이하다") Integer capacity) {
}
