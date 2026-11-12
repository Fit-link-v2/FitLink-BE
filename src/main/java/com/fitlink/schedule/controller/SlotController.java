package com.fitlink.schedule.controller;

import com.fitlink.schedule.dto.SlotChangeRequest;
import com.fitlink.schedule.dto.SlotDetailResponse;
import com.fitlink.schedule.dto.SlotListResponse;
import com.fitlink.schedule.dto.SlotSummaryResponse;
import com.fitlink.schedule.model.UpcomingSlots;
import com.fitlink.schedule.service.SlotService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/instructor/slots")
class SlotController {

	private final SlotService slots;

	SlotController(SlotService slots) {
		this.slots = slots;
	}

	@GetMapping
	SlotListResponse list(@AuthenticationPrincipal Long instructorId) {
		UpcomingSlots upcoming = slots.upcoming(instructorId);
		return new SlotListResponse(upcoming.openRangeDays(), upcoming.items().stream().map(SlotSummaryResponse::of).toList());
	}

	@GetMapping("/{id}")
	SlotDetailResponse detail(@AuthenticationPrincipal Long instructorId, @PathVariable long id) {
		return SlotDetailResponse.of(slots.detail(instructorId, id));
	}

	@PatchMapping("/{id}")
	SlotDetailResponse changeTime(@AuthenticationPrincipal Long instructorId, @PathVariable long id,
			@Valid @RequestBody SlotChangeRequest request) {
		return SlotDetailResponse.of(slots.changeTime(instructorId, id, request.startTime(), request.date()));
	}

	@PostMapping("/{id}/cancel")
	SlotDetailResponse cancel(@AuthenticationPrincipal Long instructorId, @PathVariable long id) {
		return SlotDetailResponse.of(slots.cancel(instructorId, id));
	}
}
