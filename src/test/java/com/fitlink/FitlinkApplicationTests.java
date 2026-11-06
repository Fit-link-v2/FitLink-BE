package com.fitlink;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class FitlinkApplicationTests extends IntegrationTest {

	@Test
	void health는_인증_없이_200() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void 그_밖의_경로는_인증_없으면_401() throws Exception {
		mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
	}

}
