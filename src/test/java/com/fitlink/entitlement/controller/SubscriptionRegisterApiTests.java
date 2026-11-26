package com.fitlink.entitlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.entitlement.EntitlementFixtures;
import com.fitlink.membership.MembershipFixtures;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// 오늘은 테스트 시계의 기본값 2026-11-12(목)이고, 오픈 범위는 설정 기본값 14일이라 [11-12, 11-26)이다
class SubscriptionRegisterApiTests extends IntegrationTest {

	@Autowired
	SessionRepository sessions;

	MembershipFixtures given;

	long instructorId;

	long memberId;

	Cookie session;

	@BeforeEach
	void 강사와_ACTIVE_회원() {
		given = new MembershipFixtures(jdbc);
		instructorId = given.instructor("1001");
		session = new Cookie("SESSION", sessions.issue(instructorId));
		memberId = given.member(instructorId, "김OO", "ACTIVE");
	}

	// 11-05 시작 주기(11-11 끝)는 이미 끝났고, 11-26 시작 주기는 오픈 범위 밖이다
	@Test
	void 주당_방식_8주는_8주기이고_오픈_범위_안의_주기만_바로_만든다() throws Exception {
		register(memberId, subscription("WEEK", 2, "2026-11-05", 8)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("SUBSCRIPTION"))
			.andExpect(jsonPath("$.limit.per").value("WEEK"))
			.andExpect(jsonPath("$.limit.count").value(2))
			.andExpect(jsonPath("$.startsOn").value("2026-11-05"))
			.andExpect(jsonPath("$.endsOn").value("2026-12-30"))
			.andExpect(jsonPath("$.state").value("ACTIVE"))
			.andExpect(jsonPath("$.currentPeriod.startsOn").value("2026-11-12"))
			.andExpect(jsonPath("$.currentPeriod.endsOn").value("2026-11-18"))
			.andExpect(jsonPath("$.currentPeriod.maxCount").value(2))
			.andExpect(jsonPath("$.currentPeriod.remaining").value(2));

		assertThat(subscriptions()).containsExactly(memberId + "/WEEK/2/2026-11-05/8/2026-12-30");
		assertThat(periods()).containsExactly("2026-11-12/2026-11-18/2", "2026-11-19/2026-11-25/2");
	}

	@Test
	void 응답_id는_월_정액_id이고_currentPeriod의_entitlementId는_오늘을_덮는_주기의_id다() throws Exception {
		// 두 테이블의 id가 같은 값으로 시작하므로, 다른 회원의 월 정액을 먼저 넣어 어긋나게 한다
		new EntitlementFixtures(jdbc).subscription(given.member(instructorId, "이OO", "ACTIVE"), "WEEK", 1, "2026-11-12",
				4);
		ResultActions result = register(memberId, subscription("WEEK", 2, "2026-11-05", 8))
			.andExpect(status().isCreated());

		long subscriptionId = jdbc.sql("SELECT id FROM subscription WHERE member_id = :id")
			.param("id", memberId)
			.query(Long.class)
			.single();
		long current = jdbc.sql("SELECT id FROM entitlement WHERE window_start = '2026-11-12'")
			.query(Long.class)
			.single();
		assertThat(current).isNotEqualTo(subscriptionId);
		result.andExpect(jsonPath("$.id").value(subscriptionId))
			.andExpect(jsonPath("$.currentPeriod.entitlementId").value(current));
	}

	@Test
	void 사주마다_방식_8주는_2주기이고_주기는_28일이다() throws Exception {
		register(memberId, subscription("FOUR_WEEKS", 6, "2026-11-05", 8)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.limit.per").value("FOUR_WEEKS"))
			.andExpect(jsonPath("$.endsOn").value("2026-12-30"))
			.andExpect(jsonPath("$.currentPeriod.startsOn").value("2026-11-05"))
			.andExpect(jsonPath("$.currentPeriod.endsOn").value("2026-12-02"))
			.andExpect(jsonPath("$.currentPeriod.maxCount").value(6));

		assertThat(subscriptions()).containsExactly(memberId + "/FOUR_WEEKS/6/2026-11-05/2/2026-12-30");
		assertThat(periods()).containsExactly("2026-11-05/2026-12-02/6");
	}

	@Test
	void 오픈_범위_뒤에_시작하면_주기를_만들지_않고_NOT_STARTED_currentPeriod는_null이다() throws Exception {
		register(memberId, subscription("WEEK", 1, "2026-11-26", 4)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.state").value("NOT_STARTED"))
			.andExpect(jsonPath("$.currentPeriod").value(nullValue()));

		assertThat(subscriptions()).hasSize(1);
		assertThat(periods()).isEmpty();
	}

	@Test
	void 내일_시작하면_주기는_만들지만_오늘을_덮지_않아_currentPeriod는_null이다() throws Exception {
		register(memberId, subscription("WEEK", 1, "2026-11-13", 4)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.state").value("NOT_STARTED"))
			.andExpect(jsonPath("$.currentPeriod").value(nullValue()));

		assertThat(periods()).containsExactly("2026-11-13/2026-11-19/1", "2026-11-20/2026-11-26/1");
	}

	// 마지막 주기 11-12 ~ 11-18 하나만 오픈 범위에 걸친다. 등록한 주기 수를 넘어 만들지 않는다
	@Test
	void 오늘이_마지막_주기면_그_주기만_만든다() throws Exception {
		register(memberId, subscription("WEEK", 3, "2026-10-22", 4)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.endsOn").value("2026-11-18"))
			.andExpect(jsonPath("$.state").value("ACTIVE"));

		assertThat(periods()).containsExactly("2026-11-12/2026-11-18/3");
	}

	@Test
	void 오픈_범위는_강사_설정을_따른다() throws Exception {
		jdbc.sql("UPDATE setting SET open_range_days = 28 WHERE instructor_id = :id").param("id", instructorId).update();

		register(memberId, subscription("WEEK", 1, "2026-11-12", 8)).andExpect(status().isCreated());

		assertThat(periods()).hasSize(4);
	}

	@Test
	void 오픈_범위가_더_넓은_다른_강사의_설정은_쓰지_않는다() throws Exception {
		long other = given.instructor("1002");
		jdbc.sql("UPDATE setting SET open_range_days = 28 WHERE instructor_id = :id").param("id", other).update();

		register(memberId, subscription("WEEK", 1, "2026-11-12", 8)).andExpect(status().isCreated());

		assertThat(periods()).hasSize(2);
	}

	@Test
	void 횟수와_주_경계_1_99_4_48은_받는다() throws Exception {
		register(memberId, subscription("WEEK", 1, "2026-11-12", 4)).andExpect(status().isCreated());
		register(memberId, subscription("FOUR_WEEKS", 99, "2026-11-12", 48)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.endsOn").value("2027-10-13"));

		assertThat(subscriptions()).hasSize(2);
	}

	static Stream<Arguments> 잘못된_입력() {
		return Stream.of(arguments("주 3", subscription("WEEK", 2, "2026-11-12", 3), "weeks"),
				arguments("주당 방식 5주(범위 안, 4의 배수 아님)", subscription("WEEK", 2, "2026-11-12", 5), "weeks"),
				arguments("4주마다 방식 6주(범위 안, 4의 배수 아님)", subscription("FOUR_WEEKS", 2, "2026-11-12", 6), "weeks"),
				arguments("주 0", subscription("WEEK", 2, "2026-11-12", 0), "weeks"),
				arguments("주 52", subscription("WEEK", 2, "2026-11-12", 52), "weeks"),
				arguments("주 없음",
						"{ \"kind\": \"SUBSCRIPTION\", \"limit\": { \"per\": \"WEEK\", \"count\": 2 }, \"startsOn\": \"2026-11-12\" }",
						"weeks"),
				arguments("횟수 0", subscription("WEEK", 0, "2026-11-12", 8), "limit.count"),
				arguments("횟수 100", subscription("WEEK", 100, "2026-11-12", 8), "limit.count"),
				arguments("모르는 per", subscription("MONTH", 2, "2026-11-12", 8), "limit.per"),
				arguments("limit 없음", "{ \"kind\": \"SUBSCRIPTION\", \"startsOn\": \"2026-11-12\", \"weeks\": 8 }",
						"limit"),
				arguments("limit이 객체가 아니다",
						"{ \"kind\": \"SUBSCRIPTION\", \"limit\": \"WEEK\", \"startsOn\": \"2026-11-12\", \"weeks\": 8 }",
						"limit"),
				arguments("시작일 없음",
						"{ \"kind\": \"SUBSCRIPTION\", \"limit\": { \"per\": \"WEEK\", \"count\": 2 }, \"weeks\": 8 }",
						"startsOn"),
				// 10-01 + 28일 − 1일 = 10-28
				arguments("종료일이 어제 이전", subscription("WEEK", 2, "2026-10-01", 4), "startsOn"),
				// 10-16 + 28일 − 1일 = 11-12(오늘)이면 받는다. 하루 앞인 10-15는 11-11에 끝난다
				arguments("종료일이 어제", subscription("WEEK", 2, "2026-10-15", 4), "startsOn"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("잘못된_입력")
	void 잘못된_입력은_400이고_저장하지_않는다(String name, String body, String field) throws Exception {
		register(memberId, body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value(field));

		assertThat(subscriptions()).isEmpty();
		assertThat(periods()).isEmpty();
	}

	@Test
	void 오늘_끝나는_월_정액도_등록된다() throws Exception {
		register(memberId, subscription("WEEK", 2, "2026-10-16", 4)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.endsOn").value("2026-11-12"))
			.andExpect(jsonPath("$.currentPeriod.startsOn").value("2026-11-06"));
	}

	@Test
	void 수강_종료_회원이면_422_MEMBER_ENDED이고_저장하지_않는다() throws Exception {
		long ended = given.member(instructorId, "박OO", "ENDED");

		register(ended, subscription("WEEK", 2, "2026-11-12", 8)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("MEMBER_ENDED"));

		assertThat(subscriptions()).isEmpty();
	}

	@Test
	void 다른_강사의_회원이면_404() throws Exception {
		long othersMember = given.member(given.instructor("1002"), "이OO", "ACTIVE");

		register(othersMember, subscription("WEEK", 2, "2026-11-12", 8)).andExpect(status().isNotFound());

		assertThat(subscriptions()).isEmpty();
	}

	static String subscription(String per, int count, String startsOn, int weeks) {
		return """
				{ "kind": "SUBSCRIPTION", "limit": { "per": "%s", "count": %d }, "startsOn": "%s", "weeks": %d }"""
			.formatted(per, count, startsOn, weeks);
	}

	private ResultActions register(long id, String body) throws Exception {
		return mockMvc.perform(post("/instructor/members/{id}/entitlements", id).header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private List<String> subscriptions() {
		return jdbc.sql("""
				SELECT member_id || '/' || period_unit || '/' || count_per_period || '/' || starts_on
				       || '/' || period_count || '/' || ends_on
				  FROM subscription ORDER BY id""").query(String.class).list();
	}

	// 주기 행은 kind SUBSCRIPTION이고 source_id가 있다. 사용 횟수는 0이다
	private List<String> periods() {
		return jdbc.sql("""
				SELECT window_start || '/' || window_end || '/' || max_count
				  FROM entitlement
				 WHERE kind = 'SUBSCRIPTION' AND source_id IS NOT NULL AND used_count = 0
				 ORDER BY window_start""").query(String.class).list();
	}

}
