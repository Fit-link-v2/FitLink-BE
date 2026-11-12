package com.fitlink.instructor.dto;

import com.fitlink.instructor.model.InstructorProfile;

// GET /me 응답. 카카오 동의가 없으면 name · email은 null이다
public record InstructorProfileResponse(long id, String name, String email) {

	public static InstructorProfileResponse of(InstructorProfile profile) {
		return new InstructorProfileResponse(profile.id(), profile.name(), profile.email());
	}

}
