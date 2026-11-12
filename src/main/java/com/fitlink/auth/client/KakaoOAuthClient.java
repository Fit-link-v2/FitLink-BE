package com.fitlink.auth.client;

import com.fitlink.instructor.model.KakaoUser;

// 카카오 API 호출을 이 인터페이스 뒤에 둔다. 로그인 흐름 테스트는 실제 카카오 대신 가짜 구현을 끼운다
public interface KakaoOAuthClient {

	// 인가 code로 카카오 토큰을 받고, 그 토큰으로 사용자 정보를 조회한다. 실패하면 KakaoLoginException
	KakaoUser fetchUser(String code);

}
