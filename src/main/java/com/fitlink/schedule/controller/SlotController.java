package com.fitlink.schedule.controller;

import com.fitlink.schedule.dto.ApiFormats;
import com.fitlink.schedule.model.Slot;
import com.fitlink.schedule.model.UpcomingSlots;
import com.fitlink.schedule.model.Weekday;
import com.fitlink.schedule.service.SlotService;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
	SlotList list(@AuthenticationPrincipal Long instructorId) {
		UpcomingSlots upcoming = slots.upcoming(instructorId);
		return new SlotList(upcoming.openRangeDays(), upcoming.items().stream().map(SlotSummary::of).toList());
	}

	@GetMapping("/{id}")
	SlotDetail detail(@AuthenticationPrincipal Long instructorId, @PathVariable long id) {
		return SlotDetail.of(slots.detail(instructorId, id));
	}

	@PatchMapping("/{id}")
	SlotDetail changeTime(@AuthenticationPrincipal Long instructorId, @PathVariable long id,
			@Valid @RequestBody ChangeRequest request) {
		return SlotDetail.of(slots.changeTime(instructorId, id, request.startTime(), request.date()));
	}

	@PostMapping("/{id}/cancel")
	SlotDetail cancel(@AuthenticationPrincipal Long instructorId, @PathVariable long id) {
		return SlotDetail.of(slots.cancel(instructorId, id));
	}

	record ChangeRequest(@NotNull(message = "시작 시각을 HH:mm으로 보낸다") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
			LocalDate date) {
	}

	record SlotList(int openRangeDays, List<SlotSummary> items) {
	}

	record SlotSummary(long id, String startsAt, int capacity, int taken, String canceledAt) {

		static SlotSummary of(Slot slot) {
			return new SlotSummary(slot.id(), ApiFormats.seoul(slot.startsAt()), slot.capacity(), slot.taken(),
					ApiFormats.seoul(slot.canceledAt()));
		}

	}

	record SlotDetail(long id, String startsAt, int capacity, int taken, String canceledAt, RuleView recurrence) {

		static SlotDetail of(Slot slot) {
			Slot.Rule rule = slot.recurrence();
			return new SlotDetail(slot.id(), ApiFormats.seoul(slot.startsAt()), slot.capacity(), slot.taken(),
					ApiFormats.seoul(slot.canceledAt()),
					rule == null ? null : new RuleView(rule.id(), rule.weekday(), ApiFormats.hhmm(rule.startTime())));
		}

	}

	record RuleView(long id, Weekday weekday, String startTime) {
	}

}
