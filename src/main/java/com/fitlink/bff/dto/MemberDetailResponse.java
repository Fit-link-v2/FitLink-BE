package com.fitlink.bff.dto;

import com.fitlink.bff.model.InstructorMember;

// 회원 상세. 수강권 카드(cards)는 수강권 단계에서 더한다
public record MemberDetailResponse(long id, String name, String memo, String status, boolean linkOpened) {

	public static MemberDetailResponse of(InstructorMember member) {
		return new MemberDetailResponse(member.id(), member.name(), member.memo(), member.status(),
				member.linkOpened());
	}

}
