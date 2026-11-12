package com.fitlink.instructor;

// 다른 모듈이 강사 가입을 맡길 때 넘기는 값. 이름 · 이메일은 카카오 동의가 없으면 null이다
public record InstructorSignUpCommand(long kakaoId, String name, String email) {
}
