package com.fitlink.membership.controller;

import static com.fitlink.membership.MembershipFixtures.hash;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.membership.MembershipFixtures;
import com.fitlink.membership.dto.LinkIssuedResponse;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MemberLinkApiTests extends IntegrationTest {

	static final String LINK_BASE = "http://localhost:5173/m/";

	static final byte[] OLD_HASH = hash(1);

	@Autowired
	SessionRepository sessions;

	@Autowired
	PlatformTransactionManager transactions;

	MembershipFixtures given;

	long instructorId;

	long memberId;

	Cookie session;

	@BeforeEach
	void 링크를_연_회원() {
		given = new MembershipFixtures(jdbc);
		instructorId = given.instructor("1001");
		session = new Cookie("SESSION", sessions.issue(instructorId));
		memberId = given.member(instructorId, "김OO", "ACTIVE");
		given.link(memberId, OLD_HASH, null, "2026-11-10T09:00:00+09:00");
	}

	@Test
	void 재발급하면_이전_링크를_끊고_새_링크를_만들어_201() throws Exception {
		String linkUrl = linkUrl(reissue(memberId).andExpect(status().isCreated())
			.andExpect(header().stringValues("Cache-Control", "no-store")));

		assertThat(linkUrl).startsWith(LINK_BASE);
		String token = linkUrl.substring(LINK_BASE.length());
		assertThat(token).matches("[A-Za-z0-9_-]{22}");
		assertThat(jdbc.sql("SELECT revoked_at IS NOT NULL FROM member_link WHERE token_hash = :hash")
			.param("hash", OLD_HASH)
			.query(Boolean.class)
			.single()).isTrue();
		assertThat(jdbc.sql("""
				SELECT token_hash FROM member_link
				 WHERE member_id = :id AND revoked_at IS NULL AND first_opened_at IS NULL""")
			.param("id", memberId)
			.query(byte[].class)
			.single()).isEqualTo(sha256(token));
	}

	@Test
	void 재발급한_뒤_회원은_같은_id이고_미개봉이다() throws Exception {
		reissue(memberId).andExpect(status().isCreated());

		mockMvc.perform(get("/instructor/members/{id}", memberId).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.linkOpened").value(false));
		assertThat(jdbc.sql("SELECT count(*) FROM member").query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void 이미_끊은_링크의_끊은_시각은_바뀌지_않는다() throws Exception {
		jdbc.sql("UPDATE member_link SET revoked_at = '2026-11-01T09:00:00+09:00' WHERE member_id = :id")
			.param("id", memberId)
			.update();
		given.link(memberId, hash(3), null, null);

		reissue(memberId).andExpect(status().isCreated());

		assertThat(jdbc.sql("SELECT revoked_at = '2026-11-01T09:00:00+09:00' FROM member_link WHERE token_hash = :hash")
			.param("hash", OLD_HASH)
			.query(Boolean.class)
			.single()).isTrue();
	}

	@Test
	void 수강_종료한_회원도_재발급된다() throws Exception {
		jdbc.sql("UPDATE member SET status = 'ENDED' WHERE id = :id").param("id", memberId).update();

		reissue(memberId).andExpect(status().isCreated());

		assertThat(liveLinks(memberId)).isEqualTo(1);
	}

	@Test
	void id가_숫자가_아니면_400() throws Exception {
		mockMvc.perform(post("/instructor/members/abc/link").header("X-CSRF", "1").cookie(session))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void 다른_강사의_회원과_없는_id는_같은_404이고_링크는_그대로() throws Exception {
		long others = given.member(given.instructor("2002"), "이OO", "ACTIVE");
		given.link(others, hash(2), null, null);

		reissue(others).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		reissue(others + 100).andExpect(status().isNotFound());

		assertThat(jdbc.sql("SELECT count(*) FROM member_link WHERE member_id = :id AND revoked_at IS NULL")
			.param("id", others)
			.query(Integer.class)
			.single()).isEqualTo(1);
		assertThat(jdbc.sql("SELECT count(*) FROM member_link").query(Integer.class).single()).isEqualTo(2);
	}

	// 이전 링크 행을 테스트가 잠가 두 요청이 반드시 동시에 기다리게 만든다.
	// 회원 행을 먼저 잠그면 하나는 링크 행, 하나는 회원 행에서 기다리다 차례로 돈다.
	// 잠그지 않으면 둘 다 링크 행에서 기다리다 풀린 뒤 같이 새 링크를 넣어 부분 유니크에 걸린다(500)
	@Test
	void 같은_회원에게_동시에_재발급해도_둘_다_201이고_살아_있는_링크는_하나() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(3);
		try {
			Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
				jdbc.sql("SELECT id FROM member_link WHERE token_hash = :hash FOR UPDATE")
					.param("hash", OLD_HASH)
					.query(Long.class)
					.single();
				locked.countDown();
				await(release);
			}));
			await(locked);
			List<Future<Integer>> statuses = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				statuses.add(pool.submit(() -> reissue(memberId).andReturn().getResponse().getStatus()));
			}
			awaitLockWaiters(2);
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);
			for (Future<Integer> status : statuses) {
				assertThat(status.get(10, TimeUnit.SECONDS)).isEqualTo(201);
			}
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}

		assertThat(liveLinks(memberId)).isEqualTo(1);
		assertThat(jdbc.sql("SELECT count(*) FROM member_link").query(Integer.class).single()).isEqualTo(3);
	}

	@Test
	void 로그인하지_않으면_401_X_CSRF가_없으면_403() throws Exception {
		mockMvc.perform(post("/instructor/members/{id}/link", memberId).header("X-CSRF", "1"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/instructor/members/{id}/link", memberId).cookie(session))
			.andExpect(status().isForbidden());

		assertThat(jdbc.sql("SELECT count(*) FROM member_link").query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	void 응답_객체를_문자열로_바꿔도_토큰이_나오지_않는다() throws Exception {
		String linkUrl = linkUrl(reissue(memberId).andExpect(status().isCreated()));

		assertThat(new LinkIssuedResponse(linkUrl).toString()).doesNotContain(linkUrl.substring(LINK_BASE.length()));
	}

	private ResultActions reissue(long id) throws Exception {
		return mockMvc.perform(post("/instructor/members/{id}/link", id).header("X-CSRF", "1").cookie(session));
	}

	private static String linkUrl(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.linkUrl");
	}

	private int liveLinks(long id) {
		return jdbc.sql("SELECT count(*) FROM member_link WHERE member_id = :id AND revoked_at IS NULL")
			.param("id", id)
			.query(Integer.class)
			.single();
	}

	private static byte[] sha256(String token) throws Exception {
		return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
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
