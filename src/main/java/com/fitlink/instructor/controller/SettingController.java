package com.fitlink.instructor.controller;

import com.fitlink.instructor.dto.SettingChangeRequest;
import com.fitlink.instructor.dto.SettingResponse;
import com.fitlink.instructor.service.SettingService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/instructor/settings")
class SettingController {

	private final SettingService settings;

	SettingController(SettingService settings) {
		this.settings = settings;
	}

	@GetMapping
	SettingResponse get(@AuthenticationPrincipal Long instructorId) {
		return new SettingResponse(settings.openRangeDays(instructorId));
	}

	@PatchMapping
	SettingResponse change(@AuthenticationPrincipal Long instructorId, @Valid @RequestBody SettingChangeRequest request) {
		return new SettingResponse(settings.changeOpenRangeDays(instructorId, request.openRangeDays()));
	}

}
