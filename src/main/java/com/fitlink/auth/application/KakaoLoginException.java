package com.fitlink.auth.application;

// 카카오 토큰 교환 · 사용자 조회 실패. 원인이 무엇이든 로그인 실패 주소로 보낸다.
// 메시지에 code · 토큰을 넣지 않는다
public class KakaoLoginException extends RuntimeException {

	public KakaoLoginException(String message, Throwable cause) {
		super(message, cause);
	}

}
