package com.fitlink.entitlement.controller;

import static com.fitlink.entitlement.controller.SubscriptionRegisterApiTests.subscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.entitlement.EntitlementFixtures;
import com.fitlink.membership.MembershipFixtures;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 한 회원에게 횟수권과 월 정액은 차례로만 쓴다. 오늘은 2026-11-12(목)이다
class EntitlementKindRuleApiTests extends IntegrationTest {

	@Autowired
	SessionRepository sessions;

	@Autowired
	PlatformTransactionManager transactions;

	MembershipFixtures given;

	EntitlementFixtures entitlements;

	long instructorId;

	long memberId;

	Cookie session;

	@BeforeEach
	void 강사와_ACTIVE_회원() {
		given = new MembershipFixtures(jdbc);
		entitlements = new EntitlementFixtures(jdbc);
		instructorId = given.instructor("1001");
		session = new Cookie("SESSION", sessions.issue(instructorId));
		memberId = given.member(instructorId, "김OO", "ACTIVE");
	}

	// 가장 늦게 끝나는 횟수권 12-31의 다음 날부터 월 정액을 등록할 수 있다. 기간이 겹치지 않아도 막는다
	@Test
	void 만료되지_않은_횟수권이_있으면_월_정액은_422_OTHER_KIND_ACTIVE와_availableFrom() throws Exception {
		entitlements.pass(memberId, "2026-11-01", "2026-11-30", 10);
		entitlements.pass(memberId, "2026-12-01", "2026-12-31", 10);

		register(subscription("WEEK", 2, "2027-02-01", 4)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_OTHER_KIND_ACTIVE"))
			.andExpect(jsonPath("$.availableFrom").value("2027-01-01"));

		assertThat(count("subscription")).isZero();
	}

	@Test
	void 오늘_끝나는_횟수권도_만료가_아니다() throws Exception {
		entitlements.pass(memberId, "2026-11-01", "2026-11-12", 10);

		register(subscription("WEEK", 2, "2026-11-19", 4)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_OTHER_KIND_ACTIVE"))
			.andExpect(jsonPath("$.availableFrom").value("2026-11-13"));
	}

	@Test
	void 시작_전_횟수권도_만료가_아니다() throws Exception {
		entitlements.pass(memberId, "2026-12-01", "2026-12-31", 10);

		register(subscription("WEEK", 2, "2026-11-12", 4)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_OTHER_KIND_ACTIVE"));
	}

	// 월 정액의 기간은 아직 만들지 않은 주기까지 포함한 등록 전체다. 주기 행이 하나도 없어도 막는다
	@Test
	void 만료되지_않은_월_정액이_있으면_횟수권은_422_OTHER_KIND_ACTIVE와_availableFrom() throws Exception {
		entitlements.subscription(memberId, "WEEK", 2, "2026-12-03", 8);

		registerPass("2027-03-01", "2027-03-31").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_OTHER_KIND_ACTIVE"))
			.andExpect(jsonPath("$.availableFrom").value("2027-01-28"));

		assertThat(count("entitlement")).isZero();
	}

	@Test
	void 만료된_횟수권과_기간만_겹치면_월_정액은_422_PERIOD_OVERLAP() throws Exception {
		entitlements.pass(memberId, "2026-10-01", "2026-11-10", 10);

		register(subscription("WEEK", 2, "2026-11-05", 4)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_PERIOD_OVERLAP"));

		assertThat(count("subscription")).isZero();
	}

	@Test
	void 만료된_월_정액과_기간만_겹치면_횟수권은_422_PERIOD_OVERLAP() throws Exception {
		// 10-01 ~ 10-28
		entitlements.subscription(memberId, "WEEK", 2, "2026-10-01", 4);

		registerPass("2026-10-28", "2026-12-31").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_PERIOD_OVERLAP"));
	}

	@Test
	void 만료됐고_겹치지_않으면_다른_종류도_201() throws Exception {
		entitlements.pass(memberId, "2026-10-01", "2026-11-04", 10);

		register(subscription("WEEK", 2, "2026-11-05", 4)).andExpect(status().isCreated());

		assertThat(count("subscription")).isOne();
	}

	@Test
	void 만료된_월_정액과_겹치지_않으면_횟수권은_201() throws Exception {
		entitlements.subscription(memberId, "WEEK", 2, "2026-10-01", 4);

		registerPass("2026-10-29", "2026-12-31").andExpect(status().isCreated());
	}

	@Test
	void 다른_회원의_수강권은_보지_않는다() throws Exception {
		long other = given.member(instructorId, "이OO", "ACTIVE");
		entitlements.pass(other, "2026-11-01", "2026-12-31", 10);
		entitlements.subscription(other, "WEEK", 2, "2026-11-12", 4);

		register(subscription("WEEK", 2, "2026-11-12", 4)).andExpect(status().isCreated());
		registerPass("2027-01-01", "2027-01-31").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.availableFrom").value("2026-12-10"));
	}

	@Test
	void 다른_회원의_월_정액은_횟수권_등록을_막지_않는다() throws Exception {
		entitlements.subscription(given.member(instructorId, "이OO", "ACTIVE"), "WEEK", 2, "2026-11-12", 4);

		registerPass("2026-11-12", "2026-12-31").andExpect(status().isCreated());
	}

	@Test
	void 같은_종류는_겹쳐도_201() throws Exception {
		entitlements.subscription(memberId, "WEEK", 2, "2026-11-12", 4);

		register(subscription("WEEK", 1, "2026-11-12", 4)).andExpect(status().isCreated());

		assertThat(count("subscription")).isEqualTo(2);
	}

	@Test
	void 오늘_끝나는_월_정액도_만료가_아니다() throws Exception {
		// 10-16 ~ 11-12
		entitlements.subscription(memberId, "WEEK", 2, "2026-10-16", 4);

		registerPass("2026-11-13", "2026-12-31").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_OTHER_KIND_ACTIVE"))
			.andExpect(jsonPath("$.availableFrom").value("2026-11-13"));
	}

	@Test
	void 어제_끝난_월_정액과_겹치지_않으면_횟수권은_201() throws Exception {
		// 10-15 ~ 11-11
		entitlements.subscription(memberId, "WEEK", 2, "2026-10-15", 4);

		registerPass("2026-11-12", "2026-12-31").andExpect(status().isCreated());
	}

	@Test
	void 월_정액이_여럿이면_가장_늦은_종료일의_다음_날이_availableFrom이다() throws Exception {
		// 12-09에 끝나는 것과 2027-01-06에 끝나는 것
		entitlements.subscription(memberId, "WEEK", 2, "2026-11-12", 4);
		entitlements.subscription(memberId, "WEEK", 2, "2026-11-12", 8);

		registerPass("2027-03-01", "2027-03-31").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.availableFrom").value("2027-01-07"));
	}

	@Test
	void 남은_횟수가_0인_횟수권도_만료가_아니다() throws Exception {
		entitlements.entitlement(memberId, "PASS", "2026-11-01", "2026-12-31", 10, 10, null);

		register(subscription("WEEK", 2, "2027-02-01", 4)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_OTHER_KIND_ACTIVE"));
	}

	// 첫 주기(11-05 ~ 11-11)는 이미 끝나 행을 만들지 않으므로 DB의 겹침 제약에는 걸리지 않는다
	@Test
	void 만료된_횟수권의_종료일과_월_정액의_시작일이_같아도_PERIOD_OVERLAP() throws Exception {
		entitlements.pass(memberId, "2026-10-01", "2026-11-05", 10);

		register(subscription("WEEK", 2, "2026-11-05", 4)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_PERIOD_OVERLAP"));
	}

	@Test
	void 월_정액이_만료된_횟수권을_감싸도_PERIOD_OVERLAP() throws Exception {
		// 하루짜리 횟수권 10-20을 월 정액 10-16 ~ 11-12가 감싼다.
		// 그 날의 주기(10-16 ~ 10-22)는 이미 끝나 행을 만들지 않으므로 DB의 겹침 제약에는 걸리지 않는다
		entitlements.pass(memberId, "2026-10-20", "2026-10-20", 1);

		register(subscription("WEEK", 2, "2026-10-16", 4)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_PERIOD_OVERLAP"));
	}

	@Test
	void 횟수권이_만료된_월_정액을_감싸도_PERIOD_OVERLAP() throws Exception {
		// 10-01 ~ 10-28
		entitlements.subscription(memberId, "WEEK", 2, "2026-10-01", 4);

		registerPass("2026-09-01", "2026-12-31").andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ENTITLEMENT_PERIOD_OVERLAP"));
	}

	@Test
	void 수강_종료_회원이면_다른_종류가_살아_있어도_MEMBER_ENDED가_먼저다() throws Exception {
		entitlements.pass(memberId, "2026-11-01", "2026-12-31", 10);
		jdbc.sql("UPDATE member SET status = 'ENDED' WHERE id = :id").param("id", memberId).update();

		register(subscription("WEEK", 2, "2026-11-12", 4)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("MEMBER_ENDED"));
	}

	// 다른 강사 회원의 종료일이 availableFrom으로 새지 않는다
	@Test
	void 다른_강사의_회원이면_다른_종류가_살아_있어도_404다() throws Exception {
		entitlements.pass(memberId, "2026-11-01", "2026-12-31", 10);
		session = new Cookie("SESSION", sessions.issue(given.instructor("1002")));

		register(subscription("WEEK", 2, "2026-11-12", 4)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.availableFrom").doesNotExist());
	}

	// 회원 행을 테스트가 잠가 두 등록이 반드시 동시에 기다리게 만든다. 풀면 먼저 잠근 쪽이 저장하고,
	// 늦은 쪽은 그 행을 본 뒤 검사해 422다. 등록이 회원 행을 잠그지 않으면 두 요청이 기다리지 않아 실패한다.
	// 월 정액은 오픈 범위 뒤에 시작해 주기 행이 없다. DB의 겹침 제약이 대신 막지 못하므로 잠근 뒤에 검사해야만 하나가 남는다
	@Test
	void 같은_회원에게_횟수권과_월_정액을_동시에_등록하면_하나만_201() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(3);
		List<MockHttpServletResponse> responses = new ArrayList<>();
		try {
			Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
				jdbc.sql("SELECT 1 FROM member WHERE id = :id FOR NO KEY UPDATE")
					.param("id", memberId)
					.query(Integer.class)
					.single();
				locked.countDown();
				await(release);
			}));
			await(locked);
			List<Future<MockHttpServletResponse>> results = List.of(
					pool.submit(() -> registerPass("2026-12-01", "2026-12-31").andReturn().getResponse()),
					pool.submit(() -> register(subscription("WEEK", 2, "2026-12-10", 8)).andReturn().getResponse()));
			awaitLockWaiters(2);
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);
			for (Future<MockHttpServletResponse> result : results) {
				responses.add(result.get(10, TimeUnit.SECONDS));
			}
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}

		assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 422);
		for (MockHttpServletResponse response : responses) {
			if (response.getStatus() == 422) {
				assertThat(response.getContentAsString()).contains("ENTITLEMENT_OTHER_KIND_ACTIVE");
			}
		}
		assertThat(count("subscription") + jdbc.sql("SELECT count(*) FROM entitlement WHERE kind = 'PASS'")
			.query(Integer.class)
			.single()).isOne();
	}

	private ResultActions registerPass(String startsOn, String endsOn) throws Exception {
		return register("""
				{ "kind": "PASS", "startsOn": "%s", "endsOn": "%s", "maxCount": 10 }""".formatted(startsOn, endsOn));
	}

	private ResultActions register(String body) throws Exception {
		return mockMvc.perform(post("/instructor/members/{id}/entitlements", memberId).header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private int count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
	}

	// 잠금을 기다리는 DB 연결이 count개가 될 때까지 기다린다
	private void awaitLockWaiters(int count) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (jdbc.sql("""
				SELECT count(*) FROM pg_stat_activity
				 WHERE datname = current_database() AND wait_event_type = 'Lock'""").query(Integer.class).single() < count) {
			assertThat(System.nanoTime()).as("잠금 대기 %d개", count).isLessThan(deadline);
			Thread.sleep(20);
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
