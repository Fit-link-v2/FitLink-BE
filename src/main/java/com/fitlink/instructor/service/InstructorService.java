package com.fitlink.instructor.service;

import com.fitlink.instructor.model.InstructorProfile;
import com.fitlink.instructor.InstructorSignUpCommand;
import com.fitlink.instructor.repository.InstructorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstructorService {

	private static final String KAKAO = "KAKAO";

	private final InstructorRepository instructors;

	InstructorService(InstructorRepository instructors) {
		this.instructors = instructors;
	}

	// 처음 보는 카카오 계정이면 강사와 기본 설정을 만들고, 이미 있으면 그 강사를 돌려준다
	@Transactional
	public long signUpOrFind(InstructorSignUpCommand command) {
		String providerUserId = String.valueOf(command.kakaoId());
		return instructors.insertIfAbsent(KAKAO, providerUserId, command.name(), command.email())
			.map(id -> {
				instructors.insertDefaultSetting(id);
				return id;
			})
			.orElseGet(() -> instructors.findIdByIdentity(KAKAO, providerUserId));
	}

	public InstructorProfile profile(long instructorId) {
		return instructors.findProfile(instructorId).orElseThrow();
	}

}
