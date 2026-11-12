package com.fitlink.instructor;

import com.fitlink.instructor.service.InstructorService;
import com.fitlink.instructor.service.SettingService;
import org.springframework.stereotype.Component;

// instructor 모듈이 다른 모듈에 여는 유일한 창구. 로직은 두지 않고 service에 넘긴다.
// 같은 일을 이 모듈의 컨트롤러도 하므로 로직은 service 한 곳에만 둔다
@Component
public class InstructorFacade {

	private final InstructorService instructors;

	private final SettingService settings;

	InstructorFacade(InstructorService instructors, SettingService settings) {
		this.instructors = instructors;
		this.settings = settings;
	}

	// 처음 보는 카카오 계정이면 가입시키고, 이미 있으면 그 강사의 id를 준다
	public long signUpOrFind(InstructorSignUpCommand command) {
		return instructors.signUpOrFind(command);
	}

	// 강사가 정한 오픈 범위 일수
	public int openRangeDays(long instructorId) {
		return settings.openRangeDays(instructorId);
	}

}
