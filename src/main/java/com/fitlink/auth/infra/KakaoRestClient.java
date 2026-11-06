package com.fitlink.auth.infra;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitlink.auth.AuthProperties;
import com.fitlink.auth.application.KakaoLoginException;
import com.fitlink.auth.application.KakaoOAuthClient;
import com.fitlink.instructor.application.KakaoUser;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class KakaoRestClient implements KakaoOAuthClient {

	private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";

	private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

	private final RestClient http;

	private final AuthProperties.Kakao kakao;

	@Autowired
	KakaoRestClient(AuthProperties properties) {
		this(RestClient.builder().requestFactory(withTimeouts()), properties);
	}

	// 테스트는 MockRestServiceServer를 묶은 builder를 넘긴다
	KakaoRestClient(RestClient.Builder builder, AuthProperties properties) {
		this.http = builder.build();
		this.kakao = properties.kakao();
	}

	// 카카오가 응답하지 않으면 로그인 요청을 붙잡고 있지 않고 실패 처리한다. 시간 초과도 RestClientException이다
	private static JdkClientHttpRequestFactory withTimeouts() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
		factory.setReadTimeout(Duration.ofSeconds(5));
		return factory;
	}

	@Override
	public KakaoUser fetchUser(String code) {
		try {
			return toKakaoUser(requestUser(requestAccessToken(code)));
		}
		catch (RestClientException e) {
			throw new KakaoLoginException("카카오 토큰 교환 또는 사용자 조회 실패", e);
		}
	}

	private String requestAccessToken(String code) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", kakao.clientId());
		form.add("client_secret", kakao.clientSecret());
		form.add("redirect_uri", kakao.redirectUri());
		form.add("code", code);
		return http.post()
			.uri(TOKEN_URL)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(TokenResponse.class)
			.accessToken();
	}

	private UserResponse requestUser(String accessToken) {
		return http.get().uri(USER_URL).headers(h -> h.setBearerAuth(accessToken)).retrieve().body(UserResponse.class);
	}

	// 닉네임 · 이메일은 강사가 동의하지 않으면 응답에 없다
	private static KakaoUser toKakaoUser(UserResponse user) {
		UserResponse.Account account = user.kakaoAccount();
		if (account == null) {
			return new KakaoUser(user.id(), null, null);
		}
		String nickname = account.profile() == null ? null : account.profile().nickname();
		return new KakaoUser(user.id(), nickname, account.email());
	}

	record TokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	record UserResponse(long id, @JsonProperty("kakao_account") Account kakaoAccount) {

		record Account(Profile profile, String email) {
		}

		record Profile(String nickname) {
		}

	}

}
