package com.fitlink.instructor.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class MeApiTests extends IntegrationTest {

	@Test
	void BE_0001_AC_6_쿠키_없이_GET_me는_401_UNAUTHENTICATED() throws Exception {
		mockMvc.perform(get("/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.message").isString());
	}

	@Test
	void BE_0001_AC_6_DB에_없는_쿠키_값이면_401() throws Exception {
		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", "not-a-session")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

}
