package com.fitlink.membership.dto;

import com.fitlink.membership.model.Member;
import com.fitlink.membership.model.MemberStatus;
import java.util.List;

// unopenedCount는 링크를 열지 않은 ACTIVE 회원 수다. 수강 종료(ENDED) 회원은 세지 않는다
public record MemberListResponse(long unopenedCount, List<MemberSummaryResponse> items) {

	public static MemberListResponse of(List<Member> members) {
		long unopened = members.stream()
			.filter(member -> member.status() == MemberStatus.ACTIVE && !member.linkOpened())
			.count();
		return new MemberListResponse(unopened, members.stream().map(MemberSummaryResponse::of).toList());
	}

}
