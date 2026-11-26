package com.fitlink.instructor.service;

import com.fitlink.instructor.InstructorOpenRangeExtendedEvent;
import com.fitlink.instructor.repository.SettingRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 강사 설정. setting 테이블은 이 모듈의 것이라 다른 모듈은 InstructorFacade를 거쳐 이 서비스로 읽는다.
// 예외로 회차 생성 SQL은 강사마다 다른 범위를 한 문장으로 펼치기 위해 setting을 직접 JOIN한다
@Service
public class SettingService {

	// 오픈 범위의 "오늘"은 서울 날짜다. 시계의 zone에 기대지 않고 바꿔서 구한다
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final SettingRepository settings;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	SettingService(SettingRepository settings, ApplicationEventPublisher events, Clock clock) {
		this.settings = settings;
		this.events = events;
		this.clock = clock;
	}

	// 설정 행은 가입 트랜잭션에서 함께 만들어 강사마다 항상 있다
	public int openRangeDays(long instructorId) {
		return settings.findOpenRangeDays(instructorId).orElseThrow(() -> missingRow(instructorId));
	}

	// 늘렸을 때만 이벤트를 발행한다. 받는 쪽은 같은 트랜잭션에서 동기로 돌고, 예외를 밖으로 던지면 이 변경도 되돌아간다.
	// 줄이면 범위 밖 슬롯을 지우지 않고 그대로 둔다. 이 모듈은 이벤트를 누가 받는지 모른다
	@Transactional
	public int changeOpenRangeDays(long instructorId, int openRangeDays) {
		int before = settings.lockOpenRangeDays(instructorId).orElseThrow(() -> missingRow(instructorId));
		settings.updateOpenRangeDays(instructorId, openRangeDays);
		if (openRangeDays > before) {
			OffsetDateTime now = OffsetDateTime.now(clock);
			events.publishEvent(new InstructorOpenRangeExtendedEvent(instructorId,
					now.atZoneSameInstant(SEOUL).toLocalDate(), now));
		}
		return openRangeDays;
	}

	private static IllegalStateException missingRow(long instructorId) {
		return new IllegalStateException("강사 " + instructorId + "의 설정 행이 없다");
	}

}
