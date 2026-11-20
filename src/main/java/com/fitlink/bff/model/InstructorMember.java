package com.fitlink.bff.model;

// 강사 화면에 보여 줄 회원 한 명. 다른 모듈의 Info는 service에서만 받으므로 service가 이 모양으로 바꿔 넘긴다.
// unopened(미개봉 수에 들어가는지)는 membership이 정한 값을 그대로 옮긴다
public record InstructorMember(long id, String name, String memo, String status, boolean linkOpened,
		boolean unopened) {
}
