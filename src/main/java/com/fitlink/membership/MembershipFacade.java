package com.fitlink.membership;

import com.fitlink.membership.model.Member;
import com.fitlink.membership.model.MemberStatus;
import com.fitlink.membership.service.MemberService;
import java.util.List;
import org.springframework.stereotype.Component;

// membership 모듈이 다른 모듈에 여는 유일한 창구. Info로 바꾸는 계산(미개봉 판정) 말고는 로직을 두지 않고 service에 넘긴다.
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

	// 회원 행을 잠그고 ACTIVE인지 돌려준다. 같은 회원의 수강권 등록을 한 줄로 세우는 잠금이라 부르는 쪽의 트랜잭션 안에서 부른다
	// (트랜잭션이 없으면 예외). 잠금은 그 트랜잭션이 끝날 때 풀린다. 다른 강사의 회원과 없는 회원은 똑같이 NOT_FOUND 예외다
	public boolean lockActive(long instructorId, long memberId) {
		return members.lock(instructorId, memberId) == MemberStatus.ACTIVE;
	}

	// 수강 종료(ENDED) 회원은 링크를 열지 않았어도 미개봉 수에 넣지 않는다
	private static MembershipMemberInfo info(Member member) {
		boolean unopened = member.status() == MemberStatus.ACTIVE && !member.linkOpened();
		return new MembershipMemberInfo(member.id(), member.name(), member.memo(), member.status().name(),
				member.linkOpened(), unopened);
	}

}
