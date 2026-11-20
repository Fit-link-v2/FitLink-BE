package com.fitlink.membership;

import com.fitlink.membership.model.MemberStatus;

// 다른 모듈이 받는 회원 한 명의 정보. 상태는 모듈 안의 enum 대신 이름(ACTIVE · ENDED)으로 준다.
// linkOpened는 살아 있는 링크를 회원이 한 번이라도 열었는지다
public record MembershipMemberInfo(long id, String name, String memo, String status, boolean linkOpened) {

	// 미개봉 수에 들어가는 회원인지. 수강 종료(ENDED) 회원은 링크를 열지 않았어도 세지 않는다.
	// 회원 규칙이라 받는 쪽이 따로 계산하지 않게 여기서 정한다
	public boolean unopened() {
		return MemberStatus.ACTIVE.name().equals(status) && !linkOpened;
	}

}
