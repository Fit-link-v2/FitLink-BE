package com.fitlink.instructor.model;

// 강사 프로필. 카카오 동의가 없으면 name · email은 null이다
public record InstructorProfile(long id, String name, String email) {
}
