package com.fitlink.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.infra.SessionRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

class LogoutApiTests extends IntegrationTest {

	@Autowired
	SessionRepository sessions;

	String sessionId;

	@BeforeEach
	void 로그인한_강사() {
		long id = jdbc.sql("INSERT INTO instructor (provider, provider_user_id) VALUES ('KAKAO', '1001') RETURNING id")
			.query(Long.class)
			.single();
		sessionId = sessions.issue(id);
	}

	@Test
	void POST_auth_logout은_204_쿠키_Max_Age_0_세션_행_revoked_at_채움() throws Exception {
		mockMvc.perform(post("/auth/logout").header("X-CSRF", "1").cookie(new Cookie("SESSION", sessionId)))
			.andExpect(status().isNoContent())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(containsString("SESSION="),
					containsString("Max-Age=0"), containsString("Path=/"), containsString("HttpOnly"))));

		assertThat(jdbc.sql("SELECT revoked_at IS NOT NULL FROM auth_session").query(Boolean.class).single())
			.isTrue();
	}

	@Test
	void 로그아웃_응답에는_SESSION_쿠키가_지우는_것_하나뿐이다() throws Exception {
		List<String> cookies = mockMvc
			.perform(post("/auth/logout").header("X-CSRF", "1").cookie(new Cookie("SESSION", sessionId)))
			.andReturn()
			.getResponse()
			.getHeaders(HttpHeaders.SET_COOKIE);

		assertThat(cookies).filteredOn(c -> c.startsWith("SESSION=")).singleElement().asString().contains("Max-Age=0");
	}

	@Test
	void 로그아웃한_쿠키로_GET_me는_401() throws Exception {
		mockMvc.perform(post("/auth/logout").header("X-CSRF", "1").cookie(new Cookie("SESSION", sessionId)));

		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", sessionId)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 쿠키_없이_로그아웃해도_204() throws Exception {
		mockMvc.perform(post("/auth/logout").header("X-CSRF", "1"))
			.andExpect(status().isNoContent());
	}

	@Test
	void X_CSRF_헤더_없는_로그아웃은_403_CSRF_HEADER_MISSING이고_세션은_그대로() throws Exception {
		mockMvc.perform(post("/auth/logout").cookie(new Cookie("SESSION", sessionId)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_HEADER_MISSING"));

		assertThat(jdbc.sql("SELECT revoked_at IS NULL FROM auth_session").query(Boolean.class).single())
			.isTrue();
	}

	@Test
	void 인증이_필요한_경로에_세션_헤더_없이_POST하면_401() throws Exception {
		mockMvc.perform(post("/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

}
