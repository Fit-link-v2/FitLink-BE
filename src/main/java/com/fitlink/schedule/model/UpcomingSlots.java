package com.fitlink.schedule.model;

import java.util.List;

// 지금 이후의 슬롯과 강사의 오픈 범위. 범위 밖에 남은 슬롯인지는 화면이 openRangeDays로 가른다
public record UpcomingSlots(int openRangeDays, List<Slot> items) {
}
