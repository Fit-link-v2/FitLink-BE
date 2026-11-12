package com.fitlink.auth.client;

import com.fitlink.auth.config.AuthProperties;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// 다른 패키지의 테스트가 실제 KakaoRestClient를 가짜 카카오 서버에 붙여 쓰게 한다
public final class FakeKakaoServer {

	private final MockRestServiceServer server;

	private final KakaoRestClient client;

	public FakeKakaoServer(AuthProperties properties) {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
		this.client = new KakaoRestClient(builder, properties);
	}

	public KakaoOAuthClient client() {
		return client;
	}

	// 토큰 교환은 성공하고 사용자 조회는 매번 이 상태로 실패한다. 응답 본문에 code · 토큰을 되돌려 실어 보낸다
	public void tokenThenUserFails(String accessToken, HttpStatus status, String errorBody) {
		server.expect(ExpectedCount.manyTimes(), requestTo("https://kauth.kakao.com/oauth/token"))
			.andRespond(withSuccess("{\"access_token\":\"" + accessToken + "\"}", MediaType.APPLICATION_JSON));
		server.expect(ExpectedCount.manyTimes(), requestTo("https://kapi.kakao.com/v2/user/me"))
			.andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(errorBody));
	}

	// 토큰 교환이 이 상태로 실패한다
	public void tokenFails(HttpStatus status, String errorBody) {
		server.expect(ExpectedCount.manyTimes(), requestTo("https://kauth.kakao.com/oauth/token"))
			.andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(errorBody));
	}

	// 정해 둔 카카오 주소가 실제로 불렸는지 확인한다
	public void verify() {
		server.verify();
	}

	public void reset() {
		server.reset();
	}

}
