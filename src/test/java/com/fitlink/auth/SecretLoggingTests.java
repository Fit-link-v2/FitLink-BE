package com.fitlink.auth;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.client.FakeKakaoServer;
import com.fitlink.auth.config.AuthProperties;
import com.fitlink.auth.client.KakaoUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

// 로그인 · 로그아웃 흐름을 한 번씩 다 지나간 뒤 출력된 로그 전체에 비밀 값이 없는지 본다.
// 장애를 보려고 로그 수준을 낮춘 상황도 막도록 우리 코드와 웹 · 보안 · DB 프레임워크 로그를 가장 낮은 TRACE로 돌린다
@ExtendWith(OutputCaptureExtension.class)
class SecretLoggingTests extends IntegrationTest {

	static final String ACCESS_TOKEN = "access-token-7f3a9c";

	static final String SUCCESS_URL = "http://localhost:5173/";

	static final String FAILURE_URL = "http://localhost:5173/?login=failed";

	static final List<String> TRACE_LOGGERS = List.of("com.fitlink", "org.springframework.web",
			"org.springframework.security", "org.springframework.jdbc");

	@Autowired
	LoggingSystem logging;

	@Autowired
	AuthProperties properties;

	FakeKakaoServer kakaoServer;

	String clientSecret;

	@BeforeEach
	void 로그를_TRACE로() {
		TRACE_LOGGERS.forEach(name -> logging.setLogLevel(name, LogLevel.TRACE));
		kakaoServer = new FakeKakaoServer(properties);
		clientSecret = properties.kakao().clientSecret();
	}

	@AfterEach
	void 로그_수준_되돌리기() {
		TRACE_LOGGERS.forEach(name -> logging.setLogLevel(name, null));
	}

	@Test
	void 로그인_재로그인_인증_로그아웃_흐름의_로그에_code_세션_ID가_없다(CapturedOutput output) throws Exception {
		given(kakao.fetchUser(anyString())).willReturn(new KakaoUser(1001L, "김강사", "coach@example.com"));
		List<String> secrets = new ArrayList<>(List.of("code-first-4d2e", "code-second-8b1c", clientSecret));

		// 단계마다 응답을 확인해 흐름이 중간에 끊기지 않고 끝까지 지나갔음을 보장한다
		mockMvc.perform(get("/auth/kakao")).andExpect(status().isFound());
		String first = sessionId(callback("code-first-4d2e", null, SUCCESS_URL));
		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", first))).andExpect(status().isOk());
		String second = sessionId(callback("code-second-8b1c", first, SUCCESS_URL));
		mockMvc.perform(post("/auth/logout").header("X-CSRF", "1").cookie(new Cookie("SESSION", second)))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", second))).andExpect(status().isUnauthorized());
		secrets.add(first);
		secrets.add(second);

		assertThat(output.getAll()).contains(" TRACE ").doesNotContain(secrets);
	}

	@Test
	void 카카오가_실패하고_에러_본문에_code_토큰을_실어_보내도_로그에_없다(CapturedOutput output) throws Exception {
		given(kakao.fetchUser(anyString())).willAnswer(call -> kakaoServer.client().fetchUser(call.getArgument(0)));

		kakaoServer.tokenFails(HttpStatus.BAD_REQUEST,
				"{\"error\":\"invalid_grant\",\"error_description\":\"code-token-fail-91ad " + clientSecret + "\"}");
		callback("code-token-fail-91ad", null, FAILURE_URL);
		kakaoServer.verify();
		kakaoServer.reset();
		kakaoServer.tokenThenUserFails(ACCESS_TOKEN, HttpStatus.UNAUTHORIZED,
				"{\"msg\":\"bad token " + ACCESS_TOKEN + "\",\"code\":-401}");
		callback("code-user-fail-3e7b", null, FAILURE_URL);
		kakaoServer.verify();

		assertThat(output.getAll()).contains("카카오 로그인 실패: 카카오 토큰 교환 또는 사용자 조회 실패: HTTP 400")
			.contains("카카오 로그인 실패: 카카오 토큰 교환 또는 사용자 조회 실패: HTTP 401")
			.doesNotContain("code-token-fail-91ad", "code-user-fail-3e7b", ACCESS_TOKEN, clientSecret);
	}

	// 카카오가 돌려보내는 그대로 code · state를 주소의 query string에 싣는다
	private MockHttpServletResponse callback(String code, String previousSessionId, String expectedLocation)
			throws Exception {
		var request = get("/auth/kakao/callback?code=" + code + "&state=state-1")
			.cookie(new Cookie("KAKAO-STATE", "state-1"));
		if (previousSessionId != null) {
			request.cookie(new Cookie("SESSION", previousSessionId));
		}
		return mockMvc.perform(request)
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, expectedLocation))
			.andReturn()
			.getResponse();
	}

	private static String sessionId(MockHttpServletResponse response) {
		String setCookie = response.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(c -> c.startsWith("SESSION="))
			.findFirst()
			.orElseThrow();
		return setCookie.substring("SESSION=".length(), setCookie.indexOf(';'));
	}

}
