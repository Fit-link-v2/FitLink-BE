package com.fitlink.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fitlink.IntegrationTest;
import com.fitlink.entitlement.EntitlementFixtures;
import com.fitlink.membership.MembershipFixtures;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울), 오픈 범위는 기본값 14일이라 [11-12, 11-26)이다.
// 테스트에서는 스케줄 실행을 꺼 두므로 작업 메서드를 직접 부른다
@ExtendWith(OutputCaptureExtension.class)
class PeriodJobsTests extends IntegrationTest {

	// 주기 INSERT 중 한 월 정액의 것만 실패시키는 제약. NOT VALID라 이미 있는 행은 검사하지 않는다.
	// TRUNCATE는 제약을 지우지 않으므로 테스트가 중간에 멈춰도 다음 테스트 전에 지운다
	static final String BLOCK_INSERT = "period_jobs_tests_block_insert";

	// 대상 조회를 실패시키려고 컬럼 이름을 잠시 바꾼다. 다른 테스트에 남지 않게 앞뒤와 finally에서 되돌린다
	static final String BLOCKED_COLUMN = "ends_on_period_jobs_tests";

	@Autowired
	PeriodJobs jobs;

	@Autowired
	ConfigurableApplicationContext context;

	MembershipFixtures members;

	EntitlementFixtures given;

	@BeforeEach
	void 준비() {
		dropBlockInsert();
		restoreColumn();
		members = new MembershipFixtures(jdbc);
		given = new EntitlementFixtures(jdbc);
	}

	@AfterEach
	void 정리() {
		dropBlockInsert();
		restoreColumn();
	}

	@Test
	void 하루가_지나_자정_작업이_돌면_모든_강사의_월_정액에_범위로_새로_들어온_주기가_생긴다() {
		long first = subscriptionOf(members.instructor("1001"), "ACTIVE", "2026-11-12", 8);
		long second = subscriptionOf(members.instructor("1002"), "ACTIVE", "2026-11-12", 8);
		jobs.daily();
		assertThat(periods(first)).containsExactly("2026-11-12", "2026-11-19");

		testClock.set("2026-11-13T00:00:00+09:00");
		jobs.daily();

		assertThat(periods(first)).containsExactly("2026-11-12", "2026-11-19", "2026-11-26");
		assertThat(periods(second)).containsExactly("2026-11-12", "2026-11-19", "2026-11-26");
	}

	// 1분 여유가 없으면 오늘이 11-12라 범위가 11-26 전에서 끝나 11-26 주기가 생기지 않는다
	@Test
	void 스케줄러가_자정_직전에_일찍_깨어나도_다음_날을_오늘로_보고_채운다() {
		long id = subscriptionOf(members.instructor("1001"), "ACTIVE", "2026-11-12", 8);
		testClock.set("2026-11-12T23:59:59.990+09:00");

		jobs.daily();

		assertThat(periods(id)).containsExactly("2026-11-12", "2026-11-19", "2026-11-26");
	}

	@Test
	void 앱이_시작되면_주기가_생긴다() {
		long id = subscriptionOf(members.instructor("1001"), "ACTIVE", "2026-11-12", 8);

		context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO));

		assertThat(periods(id)).containsExactly("2026-11-12", "2026-11-19");
	}

	// 등록 때 이미 만든 주기가 있어도 자정 작업이 실패하지 않는다
	@Test
	void 두_번_돌려도_같은_주기는_한_번만_생기고_실패하지_않는다(CapturedOutput output) {
		long id = subscriptionOf(members.instructor("1001"), "ACTIVE", "2026-11-12", 8);

		jobs.daily();
		jobs.daily();

		assertThat(periods(id)).containsExactly("2026-11-12", "2026-11-19");
		assertThat(output.getAll()).contains("자정 월 정액 주기 생성").doesNotContain("주기 생성 실패");
	}

	// 10-22부터 4주 등록의 마지막 주기는 11-12 ~ 11-18이다. 오픈 범위는 11-25까지지만 11-19 주기는 없다
	@Test
	void 마지막_주기_뒤로는_만들지_않는다() {
		long id = subscriptionOf(members.instructor("1001"), "ACTIVE", "2026-10-22", 4);

		jobs.daily();

		assertThat(periods(id)).containsExactly("2026-11-12");
	}

	// 10-16부터 4주 등록은 오늘(11-12) 끝난다. 마지막 주기 11-06 ~ 11-12가 오늘을 덮으므로 만든다
	@Test
	void 오늘_끝나는_월_정액도_마지막_주기를_만든다() {
		long id = subscriptionOf(members.instructor("1001"), "ACTIVE", "2026-10-16", 4);

		jobs.daily();

		assertThat(periods(id)).containsExactly("2026-11-06");
	}

	// 수강 종료는 되돌릴 수 있다. 다시 활성화했을 때 주기가 비어 있지 않게 계속 만든다
	@Test
	void 수강_종료_회원의_월_정액_주기도_만든다() {
		long id = subscriptionOf(members.instructor("1001"), "ENDED", "2026-11-12", 8);

		jobs.daily();

		assertThat(periods(id)).containsExactly("2026-11-12", "2026-11-19");
	}

	@Test
	void 한_건이_실패해도_다른_건의_주기는_생기고_실패한_건을_로그로_남긴다(CapturedOutput output) {
		long instructorId = members.instructor("1001");
		long failing = subscriptionOf(instructorId, "ACTIVE", "2026-11-12", 8);
		long other = subscriptionOf(instructorId, "ACTIVE", "2026-11-12", 8);
		jdbc.sql("ALTER TABLE entitlement ADD CONSTRAINT " + BLOCK_INSERT + " CHECK (source_id IS DISTINCT FROM "
				+ failing + ") NOT VALID").update();

		assertThatNoException().isThrownBy(jobs::daily);

		assertThat(periods(failing)).isEmpty();
		assertThat(periods(other)).containsExactly("2026-11-12", "2026-11-19");
		assertThat(output.getAll()).contains("월 정액 " + failing + " 주기 생성 실패").doesNotContain("월 정액 " + other + " 주기 생성 실패");
	}

	@Test
	void 앱_시작_때_한_건이_실패해도_예외를_밖으로_던지지_않는다() {
		long id = subscriptionOf(members.instructor("1001"), "ACTIVE", "2026-11-12", 8);
		jdbc.sql("ALTER TABLE entitlement ADD CONSTRAINT " + BLOCK_INSERT + " CHECK (source_id IS DISTINCT FROM " + id
				+ ") NOT VALID").update();

		assertThatNoException().isThrownBy(() -> context.publishEvent(
				new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO)));
		assertThat(periods(id)).isEmpty();
	}

	// 대상 조회가 실패하면 건별 처리까지 가지 못한다. 앱 시작 리스너 밖으로 예외가 나가면 Spring Boot가 앱을 멈춘다
	@Test
	void 대상_조회가_실패해도_예외를_밖으로_던지지_않고_로그를_남긴다(CapturedOutput output) {
		subscriptionOf(members.instructor("1001"), "ACTIVE", "2026-11-12", 8);
		jdbc.sql("ALTER TABLE subscription RENAME COLUMN ends_on TO " + BLOCKED_COLUMN).update();
		try {
			assertThatNoException().isThrownBy(jobs::daily);
			assertThatNoException().isThrownBy(() -> context.publishEvent(
					new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO)));
		}
		finally {
			restoreColumn();
		}

		assertThat(output.getAll()).contains("자정 월 정액 주기 생성 실패", "앱 시작 월 정액 주기 생성 실패");
		assertThat(jdbc.sql("SELECT count(*) FROM entitlement").query(Integer.class).single()).isZero();
	}

	@Test
	void 자정_작업은_서울_기준_매일_0시에_돈다() throws Exception {
		Scheduled scheduled = PeriodJobs.class.getDeclaredMethod("daily").getAnnotation(Scheduled.class);

		assertThat(scheduled).isNotNull();
		assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}

	// 회원 1명에 주당 2회 월 정액 1건
	private long subscriptionOf(long instructorId, String memberStatus, String startsOn, int weeks) {
		long memberId = members.member(instructorId, "김OO", memberStatus);
		return given.subscription(memberId, "WEEK", 2, startsOn, weeks);
	}

	private List<String> periods(long subscriptionId) {
		return jdbc.sql("SELECT window_start::text FROM entitlement WHERE source_id = :id ORDER BY window_start")
			.param("id", subscriptionId)
			.query(String.class)
			.list();
	}

	private void dropBlockInsert() {
		jdbc.sql("ALTER TABLE entitlement DROP CONSTRAINT IF EXISTS " + BLOCK_INSERT).update();
	}

	private void restoreColumn() {
		jdbc.sql("""
				DO $$ BEGIN
				  IF EXISTS (SELECT 1 FROM information_schema.columns
				              WHERE table_name = 'subscription' AND column_name = '%s') THEN
				    ALTER TABLE subscription RENAME COLUMN %s TO ends_on;
				  END IF;
				END $$""".formatted(BLOCKED_COLUMN, BLOCKED_COLUMN)).update();
	}

}
