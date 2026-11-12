package com.fitlink.schedule.controller;

import com.fitlink.schedule.dto.ApiFormats;
import com.fitlink.schedule.dto.Items;
import com.fitlink.schedule.exception.InvalidRequestException;
import com.fitlink.schedule.model.Recurrence;
import com.fitlink.schedule.model.Weekday;
import com.fitlink.schedule.service.RecurrenceService;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/instructor/recurrences")
class RecurrenceController {

	private final RecurrenceService recurrences;

	RecurrenceController(RecurrenceService recurrences) {
		this.recurrences = recurrences;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Items<RecurrenceView> register(@AuthenticationPrincipal Long instructorId,
			@Valid @RequestBody RegisterRequest request) {
		if (new HashSet<>(request.weekdays()).size() != request.weekdays().size()) {
			throw new InvalidRequestException("weekdays", "같은 요일을 두 번 고를 수 없다");
		}
		return Items.of(recurrences.register(instructorId, request.weekdays(), request.startTime(), request.capacity())
			.stream()
			.map(RecurrenceView::of)
			.toList());
	}

	@GetMapping
	Items<RecurrenceView> list(@AuthenticationPrincipal Long instructorId) {
		return Items.of(recurrences.list(instructorId).stream().map(RecurrenceView::of).toList());
	}

	@PatchMapping("/{id}")
	RecurrenceView setActive(@AuthenticationPrincipal Long instructorId, @PathVariable long id,
			@Valid @RequestBody ActiveRequest request) {
		return RecurrenceView.of(recurrences.setActive(instructorId, id, request.active()));
	}

	record RegisterRequest(@NotEmpty(message = "요일을 하나 이상 고른다") List<@NotNull(message = "요일을 하나 이상 고른다") Weekday> weekdays,
			@NotNull(message = "시작 시각을 HH:mm으로 보낸다") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
			@NotNull(message = "정원을 보낸다") @Min(value = 1, message = "정원은 1 이상 50 이하다") @Max(value = 50,
					message = "정원은 1 이상 50 이하다") Integer capacity) {
	}

	record ActiveRequest(@NotNull(message = "켜기 · 끄기를 true · false로 보낸다") Boolean active) {
	}

	record RecurrenceView(long id, Weekday weekday, String startTime, int capacity, boolean active) {

		static RecurrenceView of(Recurrence recurrence) {
			return new RecurrenceView(recurrence.id(), recurrence.weekday(), ApiFormats.hhmm(recurrence.startTime()),
					recurrence.capacity(), recurrence.active());
		}

	}

}
