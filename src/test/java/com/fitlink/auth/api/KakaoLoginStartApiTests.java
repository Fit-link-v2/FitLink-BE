package com.fitlink.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class KakaoLoginStartApiTests extends IntegrationTest {

	@Test
	void 카카오_인가_주소로_302하고_client_id_redirect_uri_response_type_state를_붙인다() throws Exception {
		URI location = location(start());

		assertThat(location.getScheme() + "://" + location.getHost() + location.getPath())
			.isEqualTo("https://kauth.kakao.com/oauth/authorize");
		Map<String, String> query = query(location);
		assertThat(query).containsEntry("client_id", "test-client-id")
			.containsEntry("redirect_uri", "http://localhost:8080/auth/kakao/callback")
			.containsEntry("response_type", "code");
		assertThat(query.get("state")).isNotBlank();
	}

	@Test
	void KAKAO_STATE_쿠키에_같은_state를_담고_HttpOnly_Secure_SameSite_Lax_Path_루트_10분() throws Exception {
		MockHttpServletResponse response = start();
		String state = query(location(response)).get("state");

		assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
			.startsWith("KAKAO-STATE=" + state + ";")
			.contains("Max-Age=600", "Path=/", "HttpOnly", "Secure", "SameSite=Lax");
	}

	@Test
	void 두_번_호출하면_state가_다르다() throws Exception {
		String first = query(location(start())).get("state");
		String second = query(location(start())).get("state");

		assertThat(second).isNotEqualTo(first);
	}

	private MockHttpServletResponse start() throws Exception {
		return mockMvc.perform(get("/auth/kakao")).andExpect(status().isFound()).andReturn().getResponse();
	}

	private static URI location(MockHttpServletResponse response) {
		return URI.create(response.getHeader(HttpHeaders.LOCATION));
	}

	// redirect_uri처럼 인코딩된 값을 풀어서 비교한다
	private static Map<String, String> query(URI uri) {
		return Arrays.stream(uri.getRawQuery().split("&"))
			.map(pair -> pair.split("=", 2))
			.collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
	}

}
