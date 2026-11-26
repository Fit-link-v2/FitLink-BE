package com.fitlink.entitlement.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 모든 월 정액의 주기를 오픈 범위 끝까지 채운다. 매일 0시(서울)에 하루가 범위에 새로 들어오고, 그 시각에 서버가 꺼져
 * 있었으면 다음 자정까지 새 주기가 비므로 앱이 시작될 때도 한 번 돈다. 시간표 회차 작업처럼 자정에는 1분 뒤 시각의 날짜를
 * 오늘로 쓴다. 시계가 서울 시간대라 그 날짜가 서울 날짜다. 서로 다른 테이블에 쓰므로 두 작업의 순서는 맞추지 않는다.
 * <p>
 * 다음 두 경우는 주기가 다음 실행까지 빌 수 있지만, 드물고 다음 실행이 채우므로 막지 않는다.
 * <ul>
 * <li>23:59에 오픈 범위를 늘리는 요청이 자정 작업보다 늦게 커밋되면, 늘어난 범위의 마지막 하루에 시작하는 주기가 빈다
 * (시간표 회차와 같다)
 * <li>월 정액 등록과 오픈 범위 늘리기가 동시에 커밋되면, 등록은 늘리기 전 범위로 주기를 만들고 늘리기는 아직 커밋되지 않은
 * 그 월 정액을 보지 못한다. 그 월 정액은 늘어난 구간 전체에 주기가 없다
 * </ul>
 */
@Component
class PeriodJobs {

	private static final Logger log = LoggerFactory.getLogger(PeriodJobs.class);

	// 스케줄러는 벽시계로 기다릴 시간을 계산하므로, 시계 보정 중에는 23:59:59.9xx에 일찍 깨어날 수 있다.
	// 그때 오늘을 그대로 읽으면 어제가 되어, 새로 범위에 들어온 주기가 다음 실행까지 빈다
	private static final Duration EARLY_FIRE_MARGIN = Duration.ofMinutes(1);

	private final PeriodGeneration generation;

	private final Clock clock;

	PeriodJobs(PeriodGeneration generation, Clock clock) {
		this.generation = generation;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
	void daily() {
		generateAll("자정", LocalDate.now(Clock.offset(clock, EARLY_FIRE_MARGIN)));
	}

	@EventListener(ApplicationReadyEvent.class)
	void onStartup() {
		generateAll("앱 시작", LocalDate.now(clock));
	}

	// 건별 실패는 PeriodGeneration이 로그로 남긴다. 대상 조회 같은 나머지 실패도 앱을 멈추지 않게 여기서 잡는다
	private void generateAll(String trigger, LocalDate today) {
		try {
			log.info("{} 월 정액 주기 생성: {}개 (오늘 {})", trigger, generation.generateAll(today), today);
		}
		catch (RuntimeException e) {
			log.error("{} 월 정액 주기 생성 실패. 다음 실행이 다시 채운다", trigger, e);
		}
	}

}
