package com.fitlink.bff.dto;

import com.fitlink.bff.model.InstructorMember;

// 회원 목록의 한 줄. 수강권 카드(frontCard)는 수강권 단계에서 더한다
public record MemberSummaryResponse(long id, String name, String memo, String status, boolean linkOpened) {

	public static MemberSummaryResponse of(InstructorMember member) {
		return new MemberSummaryResponse(member.id(), member.name(), member.memo(), member.status(),
				member.linkOpened());
	}

}
