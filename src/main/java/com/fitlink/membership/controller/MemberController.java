package com.fitlink.membership.controller;

import com.fitlink.membership.dto.LinkIssuedResponse;
import com.fitlink.membership.dto.MemberAddRequest;
import com.fitlink.membership.dto.MemberAddedResponse;
import com.fitlink.membership.dto.MemberChangeRequest;
import com.fitlink.membership.dto.MemberResponse;
import com.fitlink.membership.model.AddedMember;
import com.fitlink.membership.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

// 회원 쓰기만 둔다. 같은 주소의 목록 · 상세 조회는 수강권 카드를 함께 모으므로 bff 모듈에 있다
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
			.body(new MemberAddedResponse(MemberResponse.of(added.member()), added.link().url()));
	}


	@PatchMapping("/{id}")
	MemberResponse change(@AuthenticationPrincipal Long instructorId, @PathVariable long id,
			@RequestBody JsonNode body) {
		return MemberResponse.of(members.change(instructorId, id, MemberChangeRequest.from(body)));
	}


	// 회원 추가와 같이 링크 원본이 실린 응답이라 캐시에 남기지 않는다
	@PostMapping("/{id}/link")
	ResponseEntity<LinkIssuedResponse> reissue(@AuthenticationPrincipal Long instructorId, @PathVariable long id) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.cacheControl(CacheControl.noStore())
			.body(new LinkIssuedResponse(members.reissue(instructorId, id).url()));
	}

}
