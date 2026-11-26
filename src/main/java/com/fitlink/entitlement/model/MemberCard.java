package com.fitlink.entitlement.model;

// 카드 한 장과 그 카드의 회원. 여러 회원의 카드를 한 번에 읽은 뒤 회원별로 나눌 때 쓴다
public record MemberCard(long memberId, Card card) {
}
