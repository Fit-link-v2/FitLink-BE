package com.fitlink.membership;

// 다른 모듈이 받는 회원 한 명의 정보. 상태는 모듈 안의 enum 대신 이름(ACTIVE · ENDED)으로 준다.
// linkOpened는 살아 있는 링크를 회원이 한 번이라도 열었는지다.
// unopened는 미개봉 수에 들어가는 회원인지다. 회원 규칙이라 MembershipFacade가 계산해 담고 받는 쪽은 그대로 쓴다
public record MembershipMemberInfo(long id, String name, String memo, String status, boolean linkOpened,
		boolean unopened) {
}
