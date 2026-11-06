package com.fitlink.instructor.api;

import com.fitlink.instructor.application.InstructorProfile;
import com.fitlink.instructor.application.InstructorService;
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
	InstructorProfile me(@AuthenticationPrincipal Long instructorId) {
		return instructors.profile(instructorId);
	}

}
