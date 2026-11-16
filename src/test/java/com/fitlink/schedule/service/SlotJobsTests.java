package com.fitlink.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fitlink.IntegrationTest;
import com.fitlink.schedule.ScheduleFixtures;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울), 오픈 범위는 기본값 14일이다.
// 테스트에서는 스케줄 실행을 꺼 두므로 작업 메서드를 직접 부른다
class SlotJobsTests extends IntegrationTest {

	static final int THURSDAY = 4;

	// 회차 INSERT만 실패시키는 제약. NOT VALID라 이미 있는 행은 검사하지 않는다
	static final String BLOCK_INSERT = "slot_jobs_tests_block_insert";

	@Autowired
	SlotJobs jobs;

	@Autowired
	ConfigurableApplicationContext context;

	ScheduleFixtures given;

	@BeforeEach
	void 준비() {
		dropBlockInsert();
		given = new ScheduleFixtures(jdbc);
	}

	@AfterEach
	void 정리() {
		dropBlockInsert();
	}

	@Test
	void 하루가_지나_자정_작업이_돌면_모든_강사의_범위_끝에_새로_들어온_날의_회차가_생긴다() {
		long first = given.instructor("1001");
		long second = given.instructor("2002");
		given.recurrence(first, THURSDAY, "20:00", 8, true);
		given.recurrence(second, THURSDAY, "21:00", 8, true);
		jobs.daily();
		assertThat(days(first)).containsExactly("2026-11-12", "2026-11-19");

		testClock.set("2026-11-13T00:00:00+09:00");
		jobs.daily();

		assertThat(days(first)).containsExactly("2026-11-12", "2026-11-19", "2026-11-26");
		assertThat(days(second)).containsExactly("2026-11-12", "2026-11-19", "2026-11-26");
	}

	@Test
	void 스케줄러가_자정_직전에_일찍_깨어나도_다음_날을_오늘로_보고_채운다() {
		long instructorId = given.instructor("1001");
		given.recurrence(instructorId, THURSDAY, "20:00", 8, true);
		testClock.set("2026-11-12T23:59:59.990+09:00");

		jobs.daily();

		assertThat(days(instructorId)).containsExactly("2026-11-19", "2026-11-26");
	}

	@Test
	void 앱이_시작되면_회차가_생긴다() {
		long instructorId = given.instructor("1001");
		given.recurrence(instructorId, THURSDAY, "20:00", 8, true);

		context.publishEvent(
				new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

		assertThat(days(instructorId)).containsExactly("2026-11-12", "2026-11-19");
	}

	@Test
	void 회차_생성이_실패해도_예외를_밖으로_던지지_않는다() {
		long instructorId = given.instructor("1001");
		given.recurrence(instructorId, THURSDAY, "20:00", 8, true);
		jdbc.sql("ALTER TABLE class_slot ADD CONSTRAINT " + BLOCK_INSERT + " CHECK (false) NOT VALID").update();

		assertThatNoException().isThrownBy(jobs::daily);
		assertThatNoException().isThrownBy(() -> context.publishEvent(
				new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO)));
		assertThat(days(instructorId)).isEmpty();
	}

	@Test
	void 자정_작업은_서울_기준_매일_0시에_돈다() throws Exception {
		Scheduled scheduled = SlotJobs.class.getDeclaredMethod("daily").getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}

	private List<String> days(long instructorId) {
		return jdbc.sql("SELECT occurs_on::text FROM class_slot WHERE instructor_id = :id ORDER BY occurs_on")
			.param("id", instructorId)
			.query(String.class)
			.list();
	}

	private void dropBlockInsert() {
		jdbc.sql("ALTER TABLE class_slot DROP CONSTRAINT IF EXISTS " + BLOCK_INSERT).update();
	}

}
