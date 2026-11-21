package com.fitlink.bff.controller;

import com.fitlink.bff.dto.MemberDetailResponse;
import com.fitlink.bff.dto.MemberListResponse;
import com.fitlink.bff.service.InstructorMemberView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 같은 주소의 쓰기(회원 추가 · 수정 · 링크 재발급)는 membership에 있다. 회원과 수강권을 모으는 읽기만 여기에 둔다
@RestController
@RequestMapping("/instructor/members")
class InstructorMemberController {

	private final InstructorMemberView view;

	InstructorMemberController(InstructorMemberView view) {
		this.view = view;
	}

	@GetMapping
	MemberListResponse list(@AuthenticationPrincipal Long instructorId) {
		return MemberListResponse.of(view.members(instructorId));
	}

	@GetMapping("/{id}")
	MemberDetailResponse get(@AuthenticationPrincipal Long instructorId, @PathVariable long id) {
		return MemberDetailResponse.of(view.member(instructorId, id));
	}

}
