package com.fitlink.membership.dto;

import com.fitlink.membership.model.Member;

// 회원 추가 · 수정의 응답. membership은 수강권을 모르므로 카드 없이 회원 정보만 담는다
public record MemberResponse(long id, String name, String memo, String status, boolean linkOpened) {

	public static MemberResponse of(Member member) {
		return new MemberResponse(member.id(), member.name(), member.memo(), member.status().name(),
				member.linkOpened());
	}

}
