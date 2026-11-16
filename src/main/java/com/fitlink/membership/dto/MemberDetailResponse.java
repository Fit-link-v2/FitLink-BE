package com.fitlink.membership.dto;

import com.fitlink.membership.model.Member;

public record MemberDetailResponse(long id, String name, String memo, String status, boolean linkOpened) {

	public static MemberDetailResponse of(Member member) {
		return new MemberDetailResponse(member.id(), member.name(), member.memo(), member.status().name(),
				member.linkOpened());
	}

}
