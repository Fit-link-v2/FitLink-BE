package com.fitlink.instructor.controller;

import com.fitlink.instructor.dto.InstructorProfileResponse;
import com.fitlink.instructor.service.InstructorService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

	private final InstructorService instructors;

	MeController(InstructorService instructors) {
		this.instructors = instructors;
	}

	@GetMapping("/me")
	InstructorProfileResponse me(@AuthenticationPrincipal Long instructorId) {
		return InstructorProfileResponse.of(instructors.profile(instructorId));
	}

}
