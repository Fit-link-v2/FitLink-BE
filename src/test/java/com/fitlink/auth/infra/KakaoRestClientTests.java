package com.fitlink.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fitlink.auth.AuthProperties;
import com.fitlink.auth.application.KakaoLoginException;
import com.fitlink.instructor.application.KakaoUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

// 실제 카카오 대신 MockRestServiceServer가 정해 둔 응답을 돌려준다. DB · Spring 컨텍스트 없이 돈다
class KakaoRestClientTests {

	static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";

	static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

	MockRestServiceServer kakao;

	KakaoRestClient client;

	@BeforeEach
	void 가짜_카카오_서버() {
		RestClient.Builder builder = RestClient.builder();
		kakao = MockRestServiceServer.bindTo(builder).build();
		client = new KakaoRestClient(builder, new AuthProperties(
				new AuthProperties.Kakao("client-1", "secret-1", "http://localhost:8080/auth/kakao/callback"),
				new AuthProperties.Cookie("SESSION", "KAKAO-STATE"), "http://app/", "http://app/?login=failed"));
	}

	@Test
	void code로_토큰을_받고_그_토큰으로_사용자를_조회해_KakaoUser로_바꾼다() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", "client-1");
		form.add("client_secret", "secret-1");
		form.add("redirect_uri", "http://localhost:8080/auth/kakao/callback");
		form.add("code", "code-1");
		kakao.expect(requestTo(TOKEN_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().formData(form))
			.andRespond(withSuccess("""
					{"token_type":"bearer","access_token":"access-1","expires_in":21599}
					""", MediaType.APPLICATION_JSON));
		kakao.expect(requestTo(USER_URL))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-1"))
			.andRespond(withSuccess("""
					{"id":1001,"kakao_account":{"profile":{"nickname":"김강사"},"email":"coach@example.com"}}
					""", MediaType.APPLICATION_JSON));

		assertThat(client.fetchUser("code-1")).isEqualTo(new KakaoUser(1001L, "김강사", "coach@example.com"));
		kakao.verify();
	}

	@Test
	void kakao_account에_닉네임_이메일이_없으면_null() {
		kakao.expect(requestTo(TOKEN_URL))
			.andRespond(withSuccess("{\"access_token\":\"access-1\"}", MediaType.APPLICATION_JSON));
		kakao.expect(requestTo(USER_URL))
			.andRespond(withSuccess("{\"id\":1001,\"kakao_account\":{}}", MediaType.APPLICATION_JSON));

		assertThat(client.fetchUser("code-1")).isEqualTo(new KakaoUser(1001L, null, null));
	}

	@Test
	void kakao_account가_통째로_없어도_닉네임_이메일은_null() {
		kakao.expect(requestTo(TOKEN_URL))
			.andRespond(withSuccess("{\"access_token\":\"access-1\"}", MediaType.APPLICATION_JSON));
		kakao.expect(requestTo(USER_URL)).andRespond(withSuccess("{\"id\":1001}", MediaType.APPLICATION_JSON));

		assertThat(client.fetchUser("code-1")).isEqualTo(new KakaoUser(1001L, null, null));
	}

	@Test
	void 토큰_요청이_4xx면_KakaoLoginException() {
		kakao.expect(requestTo(TOKEN_URL)).andRespond(withBadRequest().body("{\"error_description\":\"code-1\"}"));

		assertThatThrownBy(() -> client.fetchUser("code-1")).isInstanceOf(KakaoLoginException.class)
			.hasMessageContaining("HTTP 400")
			.hasMessageNotContaining("code-1")
			.hasNoCause();
	}

	@Test
	void 사용자_조회가_5xx면_KakaoLoginException() {
		kakao.expect(requestTo(TOKEN_URL))
			.andRespond(withSuccess("{\"access_token\":\"access-1\"}", MediaType.APPLICATION_JSON));
		kakao.expect(requestTo(USER_URL)).andRespond(withServerError());

		assertThatThrownBy(() -> client.fetchUser("code-1")).isInstanceOf(KakaoLoginException.class);
	}

}
