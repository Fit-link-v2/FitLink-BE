package com.fitlink.instructor.application;

import com.fitlink.instructor.infra.InstructorRepository;
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
	public long signUpOrFind(KakaoUser user) {
		String providerUserId = String.valueOf(user.id());
		return instructors.insertIfAbsent(KAKAO, providerUserId, user.nickname(), user.email())
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
