package com.fitlink.auth.infra;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {

	private final JdbcClient jdbc;

	public SessionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// 새 세션을 만들고 쿠키에 담을 세션 ID를 돌려준다
	public String issue(long instructorId) {
		String sessionId = SessionToken.generate();
		jdbc.sql("""
				INSERT INTO auth_session (instructor_id, session_hash, expires_at)
				VALUES (:instructorId, :hash, now() + interval '14 days')
				""")
			.param("instructorId", instructorId)
			.param("hash", SessionToken.hash(sessionId))
			.update();
		return sessionId;
	}

	// 유효한 세션이면 만료를 14일 뒤로 미루고 강사 id를 돌려준다. 확인과 연장을 한 문장으로 해서 그 사이에 로그아웃이 끼어들 수 없다
	public Optional<Long> touch(String sessionId) {
		return jdbc.sql("""
				UPDATE auth_session
				   SET last_seen_at = now(), expires_at = now() + interval '14 days'
				 WHERE session_hash = :hash AND revoked_at IS NULL AND expires_at > now()
				RETURNING instructor_id
				""")
			.param("hash", SessionToken.hash(sessionId))
			.query(Long.class)
			.optional();
	}

	// 이미 무효인 세션이면 아무것도 바꾸지 않는다
	public void revoke(String sessionId) {
		jdbc.sql("UPDATE auth_session SET revoked_at = now() WHERE session_hash = :hash AND revoked_at IS NULL")
			.param("hash", SessionToken.hash(sessionId))
			.update();
	}

}
