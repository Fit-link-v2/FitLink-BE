package com.fitlink.membership.model;

// 회원 수정에서 바꿀 값. name · status가 null이면 그대로 둔다.
// 메모는 지울 수도 있어 "바꾸는지"(memoChanged)와 새 값(memo, null이면 지움)을 따로 둔다
public record MemberChange(String name, boolean memoChanged, String memo, MemberStatus status) {
}
