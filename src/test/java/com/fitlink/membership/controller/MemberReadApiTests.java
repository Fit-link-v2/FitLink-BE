package com.fitlink.membership.controller;

import static com.fitlink.membership.MembershipFixtures.hash;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.membership.MembershipFixtures;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MemberReadApiTests extends IntegrationTest {

	static final String OPENED = "2026-11-10T09:00:00+09:00";

	@Autowired
	SessionRepository sessions;

	MembershipFixtures given;

	long instructorId;

	Cookie session;

	int nextHash;

	@BeforeEach
	void 로그인한_강사() {
		given = new MembershipFixtures(jdbc);
		instructorId = given.instructor("1001");
		session = new Cookie("SESSION", sessions.issue(instructorId));
	}

	@Test
	void 목록은_미개봉_ACTIVE_나머지_ACTIVE_ENDED_순이고_묶음_안은_이름순이다() throws Exception {
		member("나", "ACTIVE", null);
		member("마", "ENDED", OPENED);
		member("다", "ACTIVE", OPENED);
		member("라", "ENDED", null);
		member("가", "ACTIVE", null);
		member(given.instructor("2002"), "갑", "ACTIVE", null);

		mockMvc.perform(get("/instructor/members").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unopenedCount").value(2))
			.andExpect(jsonPath("$.items[*].name").value(contains("가", "나", "다", "라", "마")))
			.andExpect(jsonPath("$.items[*].linkOpened").value(contains(false, false, true, false, true)))
			.andExpect(jsonPath("$.items[*].status").value(contains("ACTIVE", "ACTIVE", "ACTIVE", "ENDED", "ENDED")));
	}

	// 행이 적으면 DB가 넣은 순서대로 돌려주는 경우가 많아, 정렬에서 id를 빼도 이 테스트는 통과할 수 있다.
	// 기대 동작을 적어 두는 테스트다
	@Test
	void 같은_이름은_먼저_추가한_회원이_먼저다() throws Exception {
		long first = member("김OO", "ACTIVE", null);
		long second = member("김OO", "ACTIVE", null);

		mockMvc.perform(get("/instructor/members").cookie(session))
			.andExpect(jsonPath("$.items[*].id").value(contains((int) first, (int) second)));
	}

	// 재발급하면 이전 링크를 끊고 새 링크를 넣는다. 열었던 것은 이전 링크라 다시 미개봉이다
	@Test
	void 연_링크를_재발급하면_다시_미개봉이고_미개봉_수에_들어간다() throws Exception {
		long memberId = given.member(instructorId, "김OO", "ACTIVE");
		given.link(memberId, hash(++nextHash), "2026-11-11T09:00:00+09:00", OPENED);
		given.link(memberId, hash(++nextHash), null, null);

		mockMvc.perform(get("/instructor/members").cookie(session))
			.andExpect(jsonPath("$.unopenedCount").value(1))
			.andExpect(jsonPath("$.items[*].linkOpened").value(contains(false)));
	}

	// DB 기본 정렬 규칙(en_US 등)이면 a, B 순이다. 환경마다 순서가 달라지지 않게 코드 포인트 순으로 고정한다
	@Test
	void 이름은_코드_포인트_순이라_대문자가_소문자보다_먼저다() throws Exception {
		member("a", "ACTIVE", null);
		member("B", "ACTIVE", null);

		mockMvc.perform(get("/instructor/members").cookie(session))
			.andExpect(jsonPath("$.items[*].name").value(contains("B", "a")));
	}

	@Test
	void 회원이_없으면_빈_목록과_0() throws Exception {
		mockMvc.perform(get("/instructor/members").cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unopenedCount").value(0))
			.andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void 상세는_MemberDetail이다() throws Exception {
		long memberId = member("김OO", "ENDED", OPENED);
		jdbc.sql("UPDATE member SET memo = '목요반' WHERE id = :id").param("id", memberId).update();

		mockMvc.perform(get("/instructor/members/{id}", memberId).cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(memberId))
			.andExpect(jsonPath("$.name").value("김OO"))
			.andExpect(jsonPath("$.memo").value("목요반"))
			.andExpect(jsonPath("$.status").value("ENDED"))
			.andExpect(jsonPath("$.linkOpened").value(true));
	}

	@Test
	void 상세도_재발급한_뒤에는_미개봉이다() throws Exception {
		long memberId = given.member(instructorId, "김OO", "ACTIVE");
		given.link(memberId, hash(++nextHash), "2026-11-11T09:00:00+09:00", OPENED);
		given.link(memberId, hash(++nextHash), null, null);

		mockMvc.perform(get("/instructor/members/{id}", memberId).cookie(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.linkOpened").value(false));
	}

	@Test
	void 다른_강사의_회원과_없는_id는_같은_404() throws Exception {
		long others = member(given.instructor("2002"), "이OO", "ACTIVE", null);

		mockMvc.perform(get("/instructor/members/{id}", others).cookie(session))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get("/instructor/members/{id}", others + 100).cookie(session))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void id가_숫자가_아니면_400() throws Exception {
		mockMvc.perform(get("/instructor/members/abc").cookie(session))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void 로그인하지_않으면_401() throws Exception {
		mockMvc.perform(get("/instructor/members")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/instructor/members/1")).andExpect(status().isUnauthorized());
	}

	private long member(String name, String status, String firstOpenedAt) {
		return member(instructorId, name, status, firstOpenedAt);
	}

	// 살아 있는 링크 1개와 함께 만든다. firstOpenedAt이 있으면 회원이 그 링크를 연 것이다
	private long member(long owner, String name, String status, String firstOpenedAt) {
		long memberId = given.member(owner, name, status);
		given.link(memberId, hash(++nextHash), null, firstOpenedAt);
		return memberId;
	}

}
