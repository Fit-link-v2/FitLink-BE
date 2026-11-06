package com.fitlink.auth.application;

// 카카오 토큰 교환 · 사용자 조회 실패. 원인이 무엇이든 로그인 실패 주소로 보낸다.
// 카카오 에러 응답 본문에 code가 들어갈 수 있어 원래 예외(cause)를 담지 않고, 메시지에는 HTTP 상태나 예외 종류만 쓴다
public class KakaoLoginException extends RuntimeException {

	public KakaoLoginException(String message) {
		super(message);
	}

}
