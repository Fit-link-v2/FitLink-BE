package com.fitlink.schedule.dto;

import com.fitlink.schedule.model.ScheduleZone;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

// 시간표 응답의 시각 표기. 규칙 시각은 HH:mm, 순간은 서울 시각(+09:00)이다
public final class ApiFormats {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	private ApiFormats() {
	}

	public static String hhmm(LocalTime time) {
		return time.format(HH_MM);
	}

	// DB는 UTC로 돌려줄 수 있어 서울 시각으로 바꿔 적는다
	public static String seoul(OffsetDateTime time) {
		return time == null ? null
				: time.atZoneSameInstant(ScheduleZone.SEOUL).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

}
