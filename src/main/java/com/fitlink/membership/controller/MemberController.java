package com.fitlink.membership.controller;

import com.fitlink.membership.dto.MemberAddRequest;
import com.fitlink.membership.dto.MemberAddedResponse;
import com.fitlink.membership.dto.MemberDetailResponse;
import com.fitlink.membership.model.AddedMember;
import com.fitlink.membership.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/instructor/members")
class MemberController {

	private final MemberService members;

	MemberController(MemberService members) {
		this.members = members;
	}

	// 링크 원본이 실린 응답이라 브라우저 · 중간 캐시에 남기지 않는다.
	// 앱이 Cache-Control을 먼저 쓰면 Spring Security는 자기 기본값으로 덮어쓰지 않는다
	@PostMapping
	ResponseEntity<MemberAddedResponse> add(@AuthenticationPrincipal Long instructorId,
			@Valid @RequestBody MemberAddRequest request) {
		AddedMember added = members.add(instructorId, request.name(), request.memo());
		return ResponseEntity.status(HttpStatus.CREATED)
			.cacheControl(CacheControl.noStore())
			.body(new MemberAddedResponse(MemberDetailResponse.of(added.member()), added.link().url()));
	}

}
