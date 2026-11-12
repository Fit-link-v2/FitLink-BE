package com.fitlink.instructor.model;

// 카카오 사용자 정보 응답에서 가입에 쓰는 값. 닉네임 · 이메일은 동의하지 않으면 null이다
public record KakaoUser(long id, String nickname, String email) {
}
