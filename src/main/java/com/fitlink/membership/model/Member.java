package com.fitlink.membership.model;

// linkOpened는 살아 있는 링크를 회원이 한 번이라도 열었는지다. 재발급하면 새 링크 기준으로 다시 false가 된다
public record Member(long id, String name, String memo, MemberStatus status, boolean linkOpened) {
}
