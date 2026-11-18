package com.fitlink.membership.controller;

import static com.fitlink.membership.MembershipFixtures.hash;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.membership.MembershipFixtures;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MemberChangeApiTests extends IntegrationTest {

	@Autowired
	SessionRepository sessions;

	MembershipFixtures given;

	long instructorId;

	long memberId;

	Cookie session;

	@BeforeEach
	void 메모가_있는_회원() {
		given = new MembershipFixtures(jdbc);
		instructorId = given.instructor("1001");
		session = new Cookie("SESSION", sessions.issue(instructorId));
		memberId = given.member(instructorId, "김OO", "ACTIVE");
		jdbc.sql("UPDATE member SET memo = '목요반' WHERE id = :id").param("id", memberId).update();
		given.link(memberId, hash(1), null, null);
	}

	@Test
	void 보낸_필드만_바꾸고_MemberDetail을_준다() throws Exception {
		change(memberId, "{ \"memo\": \"화요반\" }").andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(memberId))
			.andExpect(jsonPath("$.name").value("김OO"))
			.andExpect(jsonPath("$.memo").value("화요반"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.linkOpened").value(false));

		assertThat(row()).isEqualTo("김OO/화요반/ACTIVE");
	}

	@Test
	void 이름은_앞뒤_공백을_지우고_바꾼다() throws Exception {
		change(memberId, "{ \"name\": \"  이OO  \" }").andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("이OO"));

		assertThat(row()).isEqualTo("이OO/목요반/ACTIVE");
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "{ \"memo\": null }", "{ \"memo\": \"\" }" })
	void 메모에_null이나_빈_문자열을_보내면_메모를_지운다(String body) throws Exception {
		change(memberId, body).andExpect(status().isOk()).andExpect(jsonPath("$.memo").value(nullValue()));

		assertThat(row()).isEqualTo("김OO/-/ACTIVE");
	}

	@Test
	void 빈_본문은_아무것도_바꾸지_않고_200() throws Exception {
		change(memberId, "{ }").andExpect(status().isOk()).andExpect(jsonPath("$.memo").value("목요반"));

		assertThat(row()).isEqualTo("김OO/목요반/ACTIVE");
	}

	// 본문과 400 응답의 fields[0].name
	static Stream<Arguments> badBodies() {
		return Stream.of(arguments("{ \"name\": null }", "name"), arguments("{ \"name\": \"\" }", "name"),
				arguments("{ \"name\": \"   \" }", "name"), arguments("{ \"name\": \"" + "가".repeat(51) + "\" }", "name"),
				arguments("{ \"name\": 123 }", "name"), arguments("{ \"name\": {} }", "name"),
				arguments("{ \"memo\": \"" + "가".repeat(501) + "\" }", "memo"), arguments("{ \"memo\": 123 }", "memo"),
				arguments("{ \"status\": null }", "status"), arguments("{ \"status\": \"DELETED\" }", "status"),
				arguments("{ \"status\": \"ended\" }", "status"), arguments("{ \"status\": 1 }", "status"),
				arguments("[]", "body"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("badBodies")
	void 이름을_지우거나_길이를_넘기거나_상태가_틀리면_400이고_그대로(String body, String field) throws Exception {
		change(memberId, body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fields[0].name").value(field));

		assertThat(row()).isEqualTo("김OO/목요반/ACTIVE");
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "{ \"name\": ", "" })
	void 깨진_JSON이나_빈_본문은_400이고_그대로(String body) throws Exception {
		change(memberId, body).andExpect(status().isBadRequest());

		assertThat(row()).isEqualTo("김OO/목요반/ACTIVE");
	}

	@Test
	void 여러_필드를_한_번에_바꾼다() throws Exception {
		change(memberId, "{ \"name\": \"이OO\", \"memo\": null, \"status\": \"ENDED\" }").andExpect(status().isOk());

		assertThat(row()).isEqualTo("이OO/-/ENDED");
	}

	@Test
	void 바꿀_수_있는_경계값_이름_50자와_메모_500자() throws Exception {
		change(memberId, "{ \"name\": \"" + "가".repeat(50) + "\", \"memo\": \"" + "가".repeat(500) + "\" }")
			.andExpect(status().isOk());
	}

	@Test
	void 수강_종료해도_회원과_링크는_그대로이고_다시_활성화할_수_있다() throws Exception {
		change(memberId, "{ \"status\": \"ENDED\" }").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENDED"));
		assertThat(row()).isEqualTo("김OO/목요반/ENDED");
		assertThat(jdbc.sql("SELECT count(*) FROM member_link WHERE revoked_at IS NULL").query(Integer.class).single())
			.isEqualTo(1);

		change(memberId, "{ \"status\": \"ENDED\" }").andExpect(status().isOk());
		change(memberId, "{ \"status\": \"ACTIVE\" }").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"));
		assertThat(row()).isEqualTo("김OO/목요반/ACTIVE");
	}

	@Test
	void 다른_강사의_회원과_없는_id는_같은_404이고_그대로() throws Exception {
		long others = given.member(given.instructor("2002"), "이OO", "ACTIVE");

		change(others, "{ \"status\": \"ENDED\" }").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		change(others + 100, "{ \"status\": \"ENDED\" }").andExpect(status().isNotFound());

		assertThat(jdbc.sql("SELECT status FROM member WHERE id = :id").param("id", others).query(String.class).single())
			.isEqualTo("ACTIVE");
	}

	@Test
	void 회원을_지우는_API는_없다() throws Exception {
		mockMvc.perform(delete("/instructor/members/{id}", memberId).header("X-CSRF", "1").cookie(session))
			.andExpect(status().is4xxClientError());

		assertThat(row()).isEqualTo("김OO/목요반/ACTIVE");
	}

	private ResultActions change(long id, String body) throws Exception {
		return mockMvc.perform(patch("/instructor/members/{id}", id).header("X-CSRF", "1")
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	// 메모가 없으면 '-'
	private String row() {
		return jdbc.sql("SELECT name || '/' || coalesce(memo, '-') || '/' || status FROM member WHERE id = :id")
			.param("id", memberId)
			.query(String.class)
			.single();
	}

}
