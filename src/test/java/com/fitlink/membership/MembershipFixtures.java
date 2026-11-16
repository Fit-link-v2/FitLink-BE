package com.fitlink.membership;

import org.springframework.jdbc.core.simple.JdbcClient;

// 회원 테스트의 준비 데이터를 SQL로 바로 넣는다. 만드는 API를 거치지 않아 준비 단계가 테스트 대상에 기대지 않는다
public final class MembershipFixtures {

	private final JdbcClient jdbc;

	public MembershipFixtures(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// 강사와 설정 1행
	public long instructor(String kakaoId) {
		long id = jdbc.sql("INSERT INTO instructor (provider, provider_user_id) VALUES ('KAKAO', :kakaoId) RETURNING id")
			.param("kakaoId", kakaoId)
			.query(Long.class)
			.single();
		jdbc.sql("INSERT INTO setting (instructor_id) VALUES (:id)").param("id", id).update();
		return id;
	}

	public long member(long instructorId, String name, String status) {
		return jdbc.sql("""
				INSERT INTO member (instructor_id, name, status) VALUES (:instructorId, :name, :status) RETURNING id""")
			.param("instructorId", instructorId)
			.param("name", name)
			.param("status", status)
			.query(Long.class)
			.single();
	}

	// revokedAt · firstOpenedAt이 null이면 끊지 않은 링크 · 아직 열지 않은 링크
	public long link(long memberId, byte[] tokenHash, String revokedAt, String firstOpenedAt) {
		return jdbc.sql("""
				INSERT INTO member_link (member_id, token_hash, revoked_at, first_opened_at)
				VALUES (:memberId, :tokenHash, CAST(:revokedAt AS timestamptz), CAST(:firstOpenedAt AS timestamptz))
				RETURNING id""")
			.param("memberId", memberId)
			.param("tokenHash", tokenHash)
			.param("revokedAt", revokedAt)
			.param("firstOpenedAt", firstOpenedAt)
			.query(Long.class)
			.single();
	}

	// 32바이트 해시 자리를 채우는 값. 앞 바이트만 seed로 달리한다
	public static byte[] hash(int seed) {
		byte[] hash = new byte[32];
		hash[0] = (byte) seed;
		return hash;
	}

}
