package com.fitlink.instructor.application;

import com.fitlink.instructor.infra.InstructorRepository;
import org.springframework.stereotype.Service;

@Service
public class InstructorService {

	private final InstructorRepository instructors;

	InstructorService(InstructorRepository instructors) {
		this.instructors = instructors;
	}

	public InstructorProfile profile(long instructorId) {
		return instructors.findProfile(instructorId).orElseThrow();
	}

}
