package com.fitlink.membership;

import com.fitlink.membership.model.Member;
import com.fitlink.membership.service.MemberService;
import java.util.List;
import org.springframework.stereotype.Component;

// membership 모듈이 다른 모듈에 여는 유일한 창구. 로직은 두지 않고 service에 넘긴다.
// 같은 일을 이 모듈의 컨트롤러도 하므로 로직은 service 한 곳에만 둔다
@Component
public class MembershipFacade {

	private final MemberService members;

	MembershipFacade(MemberService members) {
		this.members = members;
	}

	// 강사의 회원 전체. 순서는 회원 목록 화면 순서 그대로다
	public List<MembershipMemberInfo> members(long instructorId) {
		return members.list(instructorId).stream().map(MembershipFacade::info).toList();
	}

	// 다른 강사의 회원과 없는 회원은 똑같이 NOT_FOUND 예외다
	public MembershipMemberInfo member(long instructorId, long memberId) {
		return info(members.get(instructorId, memberId));
	}

	private static MembershipMemberInfo info(Member member) {
		return new MembershipMemberInfo(member.id(), member.name(), member.memo(), member.status().name(),
				member.linkOpened());
	}

}
