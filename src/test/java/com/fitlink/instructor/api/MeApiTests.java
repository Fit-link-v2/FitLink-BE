package com.fitlink.instructor.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.infra.SessionRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class MeApiTests extends IntegrationTest {

	@Autowired
	SessionRepository sessions;

	@Test
	void 쿠키_없이_GET_me는_401_UNAUTHENTICATED() throws Exception {
		mockMvc.perform(get("/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.message").isString());
	}

	@Test
	void DB에_없는_쿠키_값이면_401() throws Exception {
		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", "not-a-session")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 유효한_세션_쿠키로_GET_me는_200_id_name_email() throws Exception {
		long id = instructor("김강사", "coach@example.com");
		String sessionId = sessions.issue(id);

		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", sessionId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.name").value("김강사"))
			.andExpect(jsonPath("$.email").value("coach@example.com"));
	}

	@Test
	void 인증된_요청_뒤_expires_at이_요청_시각_14일() throws Exception {
		String sessionId = sessions.issue(instructor(null, null));
		jdbc.sql("UPDATE auth_session SET expires_at = now() + interval '1 day'").update();

		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", sessionId))).andExpect(status().isOk());

		assertThat(jdbc.sql("""
				SELECT abs(extract(epoch FROM expires_at - (now() + interval '14 days'))) < 60
				FROM auth_session
				""")
				.query(Boolean.class)
				.single())
			.isTrue();
	}

	@Test
	void 만료된_세션_쿠키로_GET_me는_401() throws Exception {
		String sessionId = sessions.issue(instructor(null, null));
		jdbc.sql("UPDATE auth_session SET expires_at = now() - interval '1 second'").update();

		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", sessionId)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	private long instructor(String name, String email) {
		return jdbc.sql("""
				INSERT INTO instructor (provider, provider_user_id, name, email)
				VALUES ('KAKAO', '1001', :name, :email)
				RETURNING id
				""")
			.param("name", name)
			.param("email", email)
			.query(Long.class)
			.single();
	}

}
