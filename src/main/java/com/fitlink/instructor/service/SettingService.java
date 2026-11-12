package com.fitlink.instructor.service;

import com.fitlink.instructor.repository.SettingRepository;
import org.springframework.stereotype.Service;

// 강사 설정. setting 테이블은 이 모듈의 것이라 다른 모듈의 애플리케이션 코드는 이 서비스로 읽는다.
// 예외로 회차 생성 SQL은 강사마다 다른 범위를 한 문장으로 펼치기 위해 setting을 직접 JOIN한다
@Service
public class SettingService {

	private final SettingRepository settings;

	SettingService(SettingRepository settings) {
		this.settings = settings;
	}

	// 설정 행은 가입 트랜잭션에서 함께 만들어 강사마다 항상 있다
	public int openRangeDays(long instructorId) {
		return settings.findOpenRangeDays(instructorId)
			.orElseThrow(() -> new IllegalStateException("강사 " + instructorId + "의 설정 행이 없다"));
	}

}
