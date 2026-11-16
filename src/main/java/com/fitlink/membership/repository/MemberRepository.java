package com.fitlink.membership.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {

	private final JdbcClient jdbc;

	public MemberRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// 새 회원은 ACTIVE(컬럼 기본값)
	public long insert(long instructorId, String name, String memo) {
		return jdbc.sql("""
				INSERT INTO member (instructor_id, name, memo) VALUES (:instructorId, :name, :memo) RETURNING id""")
			.param("instructorId", instructorId)
			.param("name", name)
			.param("memo", memo)
			.query(Long.class)
			.single();
	}

}
