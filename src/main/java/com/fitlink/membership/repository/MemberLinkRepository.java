package com.fitlink.membership.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// 토큰 원본은 받지 않는다. 저장하는 것은 SHA-256 해시뿐이다
@Repository
public class MemberLinkRepository {

	private final JdbcClient jdbc;

	public MemberLinkRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(long memberId, byte[] tokenHash) {
		jdbc.sql("INSERT INTO member_link (member_id, token_hash) VALUES (:memberId, :tokenHash)")
			.param("memberId", memberId)
			.param("tokenHash", tokenHash)
			.update();
	}

}
