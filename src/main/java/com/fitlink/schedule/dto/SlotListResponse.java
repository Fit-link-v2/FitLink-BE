package com.fitlink.schedule.dto;

import java.util.List;

public record SlotListResponse(int openRangeDays, List<SlotSummaryResponse> items) {
}
