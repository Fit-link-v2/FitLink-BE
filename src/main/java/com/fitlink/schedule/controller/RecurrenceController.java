package com.fitlink.schedule.controller;

import com.fitlink.common.exception.InvalidRequestException;
import com.fitlink.schedule.dto.Items;
import com.fitlink.schedule.dto.RecurrenceActiveRequest;
import com.fitlink.schedule.dto.RecurrenceRegisterRequest;
import com.fitlink.schedule.dto.RecurrenceResponse;
import com.fitlink.schedule.service.RecurrenceService;
import jakarta.validation.Valid;
import java.util.HashSet;
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
	Items<RecurrenceResponse> register(@AuthenticationPrincipal Long instructorId,
			@Valid @RequestBody RecurrenceRegisterRequest request) {
		if (new HashSet<>(request.weekdays()).size() != request.weekdays().size()) {
			throw new InvalidRequestException("weekdays", "같은 요일을 두 번 고를 수 없다");
		}
		return Items.of(recurrences.register(instructorId, request.weekdays(), request.startTime(), request.capacity())
			.stream()
			.map(RecurrenceResponse::of)
			.toList());
	}

	@GetMapping
	Items<RecurrenceResponse> list(@AuthenticationPrincipal Long instructorId) {
		return Items.of(recurrences.list(instructorId).stream().map(RecurrenceResponse::of).toList());
	}

	@PatchMapping("/{id}")
	RecurrenceResponse setActive(@AuthenticationPrincipal Long instructorId, @PathVariable long id,
			@Valid @RequestBody RecurrenceActiveRequest request) {
		return RecurrenceResponse.of(recurrences.setActive(instructorId, id, request.active()));
	}
}
