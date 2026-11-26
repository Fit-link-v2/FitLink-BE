package com.fitlink.entitlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
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

// 오늘은 테스트 시계의 기본값 2026-11-12(목)이다
class PassRegisterApiTests extends IntegrationTest {

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

	@Test
	void 횟수권을_등록하면_1행을_만들고_201_카드를_준다() throws Exception {
		register(memberId, pass("2026-11-01", "2026-12-31", 10)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.kind").value("PASS"))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.startsOn").value("2026-11-01"))
			.andExpect(jsonPath("$.endsOn").value("2026-12-31"))
			.andExpect(jsonPath("$.maxCount").value(10))
			.andExpect(jsonPath("$.remaining").value(10))
			.andExpect(jsonPath("$.state").value("ACTIVE"));

		assertThat(rows()).containsExactly(memberId + "/PASS/2026-11-01/2026-12-31/10/0/-");
	}

	@Test
	void 응답_카드의_id는_만든_행의_id다() throws Exception {
		ResultActions result = register(memberId, pass("2026-11-01", "2026-12-31", 10))
			.andExpect(status().isCreated());

		long id = jdbc.sql("SELECT id FROM entitlement").query(Long.class).single();
		result.andExpect(jsonPath("$.id").value(id));
	}

	@Test
	void 오늘_끝나는_횟수권도_등록된다() throws Exception {
		register(memberId, pass("2026-11-01", "2026-11-12", 3)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.state").value("ACTIVE"));

		assertThat(rows()).hasSize(1);
	}

	@Test
	void 시작일이_내일이면_NOT_STARTED_카드다() throws Exception {
		register(memberId, pass("2026-11-13", "2026-12-31", 10)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.state").value("NOT_STARTED"));
	}

	@Test
	void 시작일이_오늘이면_ACTIVE_카드다() throws Exception {
		register(memberId, pass("2026-11-12", "2026-12-31", 10)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.state").value("ACTIVE"));
	}

	@Test
	void 같은_기간의_횟수권을_두_번_등록하면_2행이다() throws Exception {
		register(memberId, pass("2026-11-01", "2026-12-31", 10)).andExpect(status().isCreated());
		register(memberId, pass("2026-11-01", "2026-12-31", 10)).andExpect(status().isCreated());

		assertThat(rows()).hasSize(2);
	}

	@Test
	void 횟수_경계_1과_999는_받는다() throws Exception {
		register(memberId, pass("2026-11-01", "2026-12-31", 1)).andExpect(status().isCreated());
		register(memberId, pass("2026-11-01", "2026-12-31", 999)).andExpect(status().isCreated());

		assertThat(rows()).hasSize(2);
	}

	static Stream<Arguments> 잘못된_입력() {
		return Stream.of(arguments("시작일이 종료일보다 늦다", pass("2026-11-20", "2026-11-19", 10), "endsOn"),
				arguments("횟수 0", pass("2026-11-01", "2026-12-31", 0), "maxCount"),
				arguments("횟수 1000", pass("2026-11-01", "2026-12-31", 1000), "maxCount"),
				arguments("종료일이 어제", pass("2026-11-01", "2026-11-11", 10), "endsOn"),
				arguments("kind 없음", "{ \"startsOn\": \"2026-11-01\", \"endsOn\": \"2026-12-31\", \"maxCount\": 10 }",
						"kind"),
				arguments("kind null",
						"{ \"kind\": null, \"startsOn\": \"2026-11-01\", \"endsOn\": \"2026-12-31\", \"maxCount\": 10 }",
						"kind"),
				arguments("모르는 kind",
						"{ \"kind\": \"MONTHLY\", \"startsOn\": \"2026-11-01\", \"endsOn\": \"2026-12-31\", \"maxCount\": 10 }",
						"kind"),
				arguments("시작일 없음", "{ \"kind\": \"PASS\", \"endsOn\": \"2026-12-31\", \"maxCount\": 10 }",
						"startsOn"),
				arguments("종료일 없음", "{ \"kind\": \"PASS\", \"startsOn\": \"2026-11-01\", \"maxCount\": 10 }",
						"endsOn"),
				arguments("횟수 없음", "{ \"kind\": \"PASS\", \"startsOn\": \"2026-11-01\", \"endsOn\": \"2026-12-31\" }",
						"maxCount"),
				arguments("날짜 형식이 아니다", pass("2026/11/01", "2026-12-31", 10), "startsOn"),
				arguments("없는 날짜", pass("2026-11-01", "2026-02-30", 10), "endsOn"),
				arguments("연도가 다섯 자리 이상", pass("2026-11-01", "+9999999-01-01", 10), "endsOn"),
				arguments("횟수가 정수가 아니다",
						"{ \"kind\": \"PASS\", \"startsOn\": \"2026-11-01\", \"endsOn\": \"2026-12-31\", \"maxCount\": 1.5 }",
						"maxCount"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("잘못된_입력")
	void 잘못된_입력은_400이고_저장하지_않는다(String name, String body, String field) throws Exception {
		register(memberId, body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value(field));

		assertThat(rows()).isEmpty();
	}

	@Test
	void 다른_강사의_회원이면_404() throws Exception {
		long other = given.instructor("1002");
		long othersMember = given.member(other, "이OO", "ACTIVE");

		register(othersMember, pass("2026-11-01", "2026-12-31", 10)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		assertThat(rows()).isEmpty();
	}

	@Test
	void 없는_회원이면_404() throws Exception {
		register(memberId + 100, pass("2026-11-01", "2026-12-31", 10)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void 수강_종료_회원이면_422_MEMBER_ENDED이고_저장하지_않는다() throws Exception {
		long ended = given.member(instructorId, "박OO", "ENDED");

		register(ended, pass("2026-11-01", "2026-12-31", 10)).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("MEMBER_ENDED"));

		assertThat(rows()).isEmpty();
	}

	@Test
	void 입력이_틀리면_없는_회원이어도_400이_먼저다() throws Exception {
		register(memberId + 100, pass("2026-11-01", "2026-12-31", 0)).andExpect(status().isBadRequest());
	}

	@Test
	void 입력이_틀리면_수강_종료_회원이어도_400이_먼저다() throws Exception {
		long ended = given.member(instructorId, "박OO", "ENDED");

		register(ended, pass("2026-11-01", "2026-11-11", 10)).andExpect(status().isBadRequest());
	}

	private static String pass(String startsOn, String endsOn, int maxCount) {
		return """
				{ "kind": "PASS", "startsOn": "%s", "endsOn": "%s", "maxCount": %d }""".formatted(startsOn, endsOn,
				maxCount);
	}

	private ResultActions register(long id, String body) throws Exception {
		return mockMvc.perform(post("/instructor/members/{id}/entitlements", id).header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	// source_id가 없으면 '-'
	private List<String> rows() {
		return jdbc.sql("""
				SELECT member_id || '/' || kind || '/' || window_start || '/' || window_end || '/' || max_count
				       || '/' || used_count || '/' || coalesce(source_id::text, '-')
				  FROM entitlement ORDER BY id""").query(String.class).list();
	}

}
