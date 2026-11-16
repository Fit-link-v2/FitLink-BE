package com.fitlink.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.membership.MembershipFixtures;
import com.fitlink.membership.dto.MemberAddedResponse;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MemberApiTests extends IntegrationTest {

	// application.yml의 회원 화면 주소 기본값
	static final String LINK_BASE = "http://localhost:5173/m/";

	// 링크 INSERT만 실패시키는 제약. NOT VALID라 이미 있는 행은 검사하지 않는다.
	// TRUNCATE는 제약을 지우지 않으므로 테스트가 중간에 멈춰도 다음 테스트 전에 지운다
	static final String BLOCK_LINK = "member_api_tests_block_link";

	@Autowired
	SessionRepository sessions;

	MembershipFixtures given;

	long instructorId;

	Cookie session;

	@BeforeEach
	void 로그인한_강사() {
		dropBlockLink();
		given = new MembershipFixtures(jdbc);
		instructorId = given.instructor("1001");
		session = new Cookie("SESSION", sessions.issue(instructorId));
	}

	@Test
	void 회원을_추가하면_이름_앞뒤_공백을_지우고_ACTIVE_미개봉으로_만들고_살아_있는_링크가_하나_생긴다() throws Exception {
		add("{ \"name\": \"  김OO  \", \"memo\": \"목요반\" }").andExpect(status().isCreated())
			.andExpect(jsonPath("$.member.id").isNumber())
			.andExpect(jsonPath("$.member.name").value("김OO"))
			.andExpect(jsonPath("$.member.memo").value("목요반"))
			.andExpect(jsonPath("$.member.status").value("ACTIVE"))
			.andExpect(jsonPath("$.member.linkOpened").value(false));

		assertThat(jdbc.sql("SELECT name || '/' || memo || '/' || status FROM member WHERE instructor_id = :id")
			.param("id", instructorId)
			.query(String.class)
			.list()).containsExactly("김OO/목요반/ACTIVE");
		assertThat(liveLinkCount()).isEqualTo(1);
	}

	@Test
	void 링크_주소는_설정한_회원_화면_주소와_22자_토큰이고_DB에는_토큰의_SHA_256만_있다() throws Exception {
		String linkUrl = linkUrl(add("{ \"name\": \"김OO\" }").andExpect(status().isCreated()));

		assertThat(linkUrl).startsWith(LINK_BASE);
		String token = linkUrl.substring(LINK_BASE.length());
		assertThat(token).matches("[A-Za-z0-9_-]{22}");
		assertThat(jdbc.sql("SELECT token_hash FROM member_link").query(byte[].class).single())
			.isEqualTo(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
	}

	// Spring Security가 모든 응답에 붙이는 기본값(no-cache, no-store, max-age=0, must-revalidate)이 아니라
	// 컨트롤러가 직접 붙인 값인지 본다
	@Test
	void 링크를_주는_응답의_Cache_Control은_정확히_no_store() throws Exception {
		add("{ \"name\": \"김OO\" }").andExpect(status().isCreated())
			.andExpect(header().stringValues("Cache-Control", "no-store"));
	}

	@Test
	void 빈_메모는_null로_저장한다() throws Exception {
		add("{ \"name\": \"김OO\", \"memo\": \"\" }").andExpect(status().isCreated())
			.andExpect(jsonPath("$.member.memo").value(nullValue()));

		assertThat(jdbc.sql("SELECT memo IS NULL FROM member").query(Boolean.class).single()).isTrue();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("badNames")
	void 이름이_없거나_공백뿐이거나_50자를_넘으면_400이고_아무것도_저장하지_않는다(String body) throws Exception {
		add(body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value("name"));

		assertThat(rowCount("member")).isZero();
		assertThat(rowCount("member_link")).isZero();
	}

	static List<String> badNames() {
		return List.of("{ }", "{ \"name\": \"\" }", "{ \"name\": \"   \" }",
				"{ \"name\": \"" + "가".repeat(51) + "\" }");
	}

	@Test
	void 메모가_500자를_넘으면_400() throws Exception {
		add("{ \"name\": \"김OO\", \"memo\": \"" + "가".repeat(501) + "\" }").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields[0].name").value("memo"));

		assertThat(rowCount("member")).isZero();
		assertThat(rowCount("member_link")).isZero();
	}

	// 길이는 앞뒤 공백을 지운 뒤에 센다
	@Test
	void 공백을_지운_이름_50자와_메모_500자는_저장된다() throws Exception {
		add("{ \"name\": \"  " + "가".repeat(50) + "  \", \"memo\": \"" + "가".repeat(500) + "\" }")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.member.name").value("가".repeat(50)));
	}

	@Test
	void 응답_객체를_문자열로_바꿔도_토큰이_나오지_않는다() throws Exception {
		String linkUrl = linkUrl(add("{ \"name\": \"김OO\" }").andExpect(status().isCreated()));
		String token = linkUrl.substring(LINK_BASE.length());

		assertThat(new MemberAddedResponse(null, linkUrl).toString()).doesNotContain(token);
	}

	@Test
	void 같은_이름의_회원을_또_추가할_수_있다() throws Exception {
		add("{ \"name\": \"김OO\" }").andExpect(status().isCreated());
		add("{ \"name\": \"김OO\" }").andExpect(status().isCreated());

		assertThat(rowCount("member")).isEqualTo(2);
		assertThat(liveLinkCount()).isEqualTo(2);
	}

	@Test
	void 링크_저장이_실패하면_회원도_남지_않는다() throws Exception {
		jdbc.sql("ALTER TABLE member_link ADD CONSTRAINT " + BLOCK_LINK + " CHECK (false) NOT VALID").update();
		try {
			add("{ \"name\": \"김OO\" }").andExpect(status().isInternalServerError());
		}
		finally {
			dropBlockLink();
		}

		assertThat(rowCount("member")).isZero();
	}

	@Test
	void 로그인하지_않으면_401_X_CSRF가_없으면_403() throws Exception {
		mockMvc.perform(post("/instructor/members").header("X-CSRF", "1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"name\": \"김OO\" }")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/instructor/members").cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"name\": \"김OO\" }")).andExpect(status().isForbidden());

		assertThat(rowCount("member")).isZero();
	}

	private ResultActions add(String body) throws Exception {
		return mockMvc.perform(post("/instructor/members").header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static String linkUrl(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.linkUrl");
	}

	private int liveLinkCount() {
		return jdbc.sql("SELECT count(*) FROM member_link WHERE revoked_at IS NULL").query(Integer.class).single();
	}

	private int rowCount(String table) {
		List<Integer> counts = jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).list();
		return counts.getFirst();
	}

	private void dropBlockLink() {
		jdbc.sql("ALTER TABLE member_link DROP CONSTRAINT IF EXISTS " + BLOCK_LINK).update();
	}

}
