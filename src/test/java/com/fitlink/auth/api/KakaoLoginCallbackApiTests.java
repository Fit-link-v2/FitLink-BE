package com.fitlink.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.application.KakaoLoginException;
import com.fitlink.instructor.application.KakaoUser;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class KakaoLoginCallbackApiTests extends IntegrationTest {

	static final String SUCCESS_URL = "http://localhost:5173/";

	static final String FAILURE_URL = "http://localhost:5173/?login=failed";

	@BeforeEach
	void 카카오가_돌려줄_사용자() {
		given(kakao.fetchUser("code-1")).willReturn(new KakaoUser(1001L, "김강사", "coach@example.com"));
	}

	@Test
	void 성공하면_302_성공_주소_SESSION_쿠키_세션_행_KAKAO_STATE_삭제() throws Exception {
		MockHttpServletResponse response = perform(callback("code-1", "state-1").cookie(state("state-1")));

		assertThat(response.getStatus()).isEqualTo(302);
		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(SUCCESS_URL);
		String session = setCookie(response, "SESSION");
		assertThat(session).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/");
		String sessionId = cookieValue(session);
		assertThat(jdbc.sql("SELECT session_hash FROM auth_session").query(byte[].class).single())
			.isEqualTo(sha256(sessionId));
		assertThat(setCookie(response, "KAKAO-STATE")).contains("Max-Age=0");
	}

	@Test
	void 요청_파라미터로_목적지를_바꿀_수_없다() throws Exception {
		MockHttpServletResponse response = perform(callback("code-1", "state-1").param("redirect", "https://evil.example")
			.param("next", "https://evil.example")
			.param("redirect_uri", "https://evil.example")
			.cookie(state("state-1")));

		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(SUCCESS_URL);
	}

	@Test
	void 이전_SESSION_쿠키를_들고_로그인하면_새_값이_다르고_이전_세션은_revoked() throws Exception {
		MockHttpServletResponse first = perform(callback("code-1", "state-1").cookie(state("state-1")));
		String oldId = cookieValue(setCookie(first, "SESSION"));

		MockHttpServletResponse second = perform(
				callback("code-1", "state-2").cookie(state("state-2"), new Cookie("SESSION", oldId)));
		String newId = cookieValue(setCookie(second, "SESSION"));

		assertThat(second.getHeaders(HttpHeaders.SET_COOKIE)).filteredOn(c -> c.startsWith("SESSION=")).hasSize(1);
		assertThat(newId).isNotEqualTo(oldId);
		assertThat(jdbc.sql("SELECT revoked_at IS NOT NULL FROM auth_session WHERE session_hash = ?")
				.param(sha256(oldId))
				.query(Boolean.class)
				.single())
			.isTrue();
		assertThat(jdbc.sql("SELECT revoked_at IS NULL FROM auth_session WHERE session_hash = ?")
				.param(sha256(newId))
				.query(Boolean.class)
				.single())
			.isTrue();
	}

	@Test
	void 카카오가_error를_주면_302_실패_주소이고_세션_강사_행이_없다() throws Exception {
		MockHttpServletResponse response = perform(get("/auth/kakao/callback").param("error", "access_denied")
			.param("state", "state-1")
			.cookie(state("state-1")));

		assertFailed(response);
		then(kakao).shouldHaveNoInteractions();
	}

	@Test
	void KAKAO_STATE_쿠키가_없으면_302_실패_주소() throws Exception {
		MockHttpServletResponse response = perform(callback("code-1", "state-1"));

		assertFailed(response);
		then(kakao).shouldHaveNoInteractions();
	}

	@Test
	void state가_쿠키와_다르면_302_실패_주소() throws Exception {
		MockHttpServletResponse response = perform(callback("code-1", "state-1").cookie(state("other")));

		assertFailed(response);
		then(kakao).shouldHaveNoInteractions();
	}

	@Test
	void 카카오_호출이_실패하면_302_실패_주소() throws Exception {
		given(kakao.fetchUser("code-2")).willThrow(new KakaoLoginException("카카오 실패"));

		MockHttpServletResponse response = perform(callback("code-2", "state-1").cookie(state("state-1")));

		assertFailed(response);
	}

	// SESSION 쿠키 없음, 강사 · 세션 행 없음, state 쿠키는 지움
	private void assertFailed(MockHttpServletResponse response) {
		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(FAILURE_URL);
		assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).noneMatch(c -> c.startsWith("SESSION="));
		assertThat(setCookie(response, "KAKAO-STATE")).contains("Max-Age=0");
		assertThat(jdbc.sql("SELECT count(*) FROM instructor").query(Long.class).single()).isZero();
		assertThat(jdbc.sql("SELECT count(*) FROM auth_session").query(Long.class).single()).isZero();
	}

	private static MockHttpServletRequestBuilder callback(String code, String state) {
		return get("/auth/kakao/callback").param("code", code).param("state", state);
	}

	private static Cookie state(String value) {
		return new Cookie("KAKAO-STATE", value);
	}

	private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isFound()).andReturn().getResponse();
	}

	private static String setCookie(MockHttpServletResponse response, String name) {
		List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
		return cookies.stream().filter(c -> c.startsWith(name + "=")).findFirst().orElseThrow();
	}

	private static String cookieValue(String setCookie) {
		return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
	}

	private static byte[] sha256(String value) throws Exception {
		return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
	}

}
