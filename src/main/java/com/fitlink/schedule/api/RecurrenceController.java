package com.fitlink.schedule.api;

import com.fitlink.schedule.application.Recurrence;
import com.fitlink.schedule.application.RecurrenceService;
import com.fitlink.schedule.application.Weekday;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/instructor/recurrences")
class RecurrenceController {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

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

	record RegisterRequest(@NotEmpty(message = "요일을 하나 이상 고른다") List<@NotNull(message = "요일을 하나 이상 고른다") Weekday> weekdays,
			@NotNull(message = "시작 시각을 HH:mm으로 보낸다") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
			@NotNull(message = "정원을 보낸다") @Min(value = 1, message = "정원은 1 이상 50 이하다") @Max(value = 50,
					message = "정원은 1 이상 50 이하다") Integer capacity) {
	}

	record RecurrenceView(long id, Weekday weekday, String startTime, int capacity, boolean active) {

		static RecurrenceView of(Recurrence recurrence) {
			return new RecurrenceView(recurrence.id(), recurrence.weekday(), recurrence.startTime().format(HH_MM),
					recurrence.capacity(), recurrence.active());
		}

	}

}
