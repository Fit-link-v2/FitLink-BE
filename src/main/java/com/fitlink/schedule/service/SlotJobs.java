package com.fitlink.schedule.service;

import com.fitlink.schedule.model.GenerationTime;
import com.fitlink.schedule.model.ScheduleZone;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 모든 강사의 회차를 오픈 범위 끝까지 채운다. 매일 0시(서울)에 하루가 범위에 새로 들어오고,
 * 그 시각에 서버가 꺼져 있었으면 다음 자정까지 그날이 비므로 앱이 시작될 때도 한 번 돈다.
 * 매번 범위 전체를 다시 보므로 하루 실패해도 다음 실행이 채운다. 그래서 실패는 로그만 남기고 앱을 멈추지 않는다.
 * <p>
 * 23:59에 오픈 범위를 늘리는 요청이 자정 작업보다 늦게 커밋되면, 새 범위의 마지막 하루가 다음 실행까지 빈다.
 * 드물고 다음 실행이 채우므로 막지 않는다. 생성 SQL에 FOR SHARE를 거는 것으로는 막지 못한다: 잠금을 기다린 뒤
 * PostgreSQL은 이미 펼친 날짜 행만 새 값으로 다시 검사하고, 늘어난 날짜 행을 새로 만들지 않는다
 */
@Component
class SlotJobs {

	private static final Logger log = LoggerFactory.getLogger(SlotJobs.class);

	// 스케줄러는 벽시계로 기다릴 시간을 계산하므로, 시계 보정 중에는 23:59:59.9xx에 일찍 깨어날 수 있다.
	// 그때 오늘을 그대로 읽으면 어제가 되어, 새로 범위에 들어온 날이 다음 실행까지 빈다
	private static final Duration EARLY_FIRE_MARGIN = Duration.ofMinutes(1);

	private final SlotGeneration generation;

	private final Clock clock;

	SlotJobs(SlotGeneration generation, Clock clock) {
		this.generation = generation;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
	void daily() {
		OffsetDateTime now = OffsetDateTime.now(clock);
		generateAll("자정", new GenerationTime(
				now.plus(EARLY_FIRE_MARGIN).atZoneSameInstant(ScheduleZone.SEOUL).toLocalDate(), now));
	}

	@EventListener(ApplicationReadyEvent.class)
	void onStartup() {
		generateAll("앱 시작", GenerationTime.at(clock));
	}

	// 앱 시작 때 예외가 밖으로 나가면 Spring Boot가 앱을 멈추므로 여기서 잡는다
	private void generateAll(String trigger, GenerationTime time) {
		try {
			int created = generation.generateAll(time);
			log.info("{} 회차 생성: {}개 (오늘 {})", trigger, created, time.today());
		}
		catch (RuntimeException e) {
			log.error("{} 회차 생성 실패. 다음 실행이 다시 채운다", trigger, e);
		}
	}

}
