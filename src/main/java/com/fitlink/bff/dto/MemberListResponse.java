package com.fitlink.bff.dto;

import com.fitlink.bff.model.InstructorMember;
import java.util.List;

// unopenedCount는 링크를 열지 않은 ACTIVE 회원 수다. 어떤 회원을 세는지는 membership이 정한다
public record MemberListResponse(long unopenedCount, List<MemberSummaryResponse> items) {

	public static MemberListResponse of(List<InstructorMember> members) {
		long unopened = members.stream().filter(InstructorMember::unopened).count();
		return new MemberListResponse(unopened, members.stream().map(MemberSummaryResponse::of).toList());
	}

}
