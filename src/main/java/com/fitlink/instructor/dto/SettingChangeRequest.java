package com.fitlink.instructor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SettingChangeRequest(@NotNull(message = "오픈 범위 일수를 보낸다") @Min(value = 7,
		message = "7 ~ 28일 사이로 고른다") @Max(value = 28, message = "7 ~ 28일 사이로 고른다") Integer openRangeDays) {
}
