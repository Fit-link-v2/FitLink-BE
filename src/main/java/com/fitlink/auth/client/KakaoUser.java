package com.fitlink.auth.client;

// 카카오 사용자 정보 응답에서 가입에 쓰는 값. instructor 모듈에는 InstructorSignUpCommand로 바꿔 넘긴다. 닉네임 · 이메일은 동의하지 않으면 null이다
public record KakaoUser(long id, String nickname, String email) {
}
