package com.fitlink.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitlink.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SessionRepositoryTests extends IntegrationTest {

	@Autowired
	SessionRepository sessions;

	long instructorId;

	@BeforeEach
	void 강사_한_명() {
		instructorId = jdbc.sql("INSERT INTO instructor (provider, provider_user_id) VALUES ('KAKAO', '1001') RETURNING id")
			.query(Long.class)
			.single();
	}

	@Test
	void 발급한_세션_ID는_32바이트_난수이고_DB에는_SHA_256_해시만_저장된다() throws Exception {
		String sessionId = sessions.issue(instructorId);
		String other = sessions.issue(instructorId);

		assertThat(Base64.getUrlDecoder().decode(sessionId)).hasSize(32);
		assertThat(other).isNotEqualTo(sessionId);

		byte[] expected = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.US_ASCII));
		assertThat(jdbc.sql("SELECT session_hash FROM auth_session WHERE instructor_id = ? ORDER BY id")
				.param(instructorId)
				.query(byte[].class)
				.list())
			.first()
			.isEqualTo(expected);
	}

	// 컬럼을 하나라도 더하면 이 테스트가 깨진다. 세션 테이블에 무엇을 저장하는지 리뷰에서 다시 보게 하려는 의도다
	@Test
	void 세션_테이블에는_세션_ID_원본_컬럼이_없고_어느_테이블에도_카카오_토큰_컬럼이_없다() {
		assertThat(jdbc.sql("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'auth_session'
				""").query(String.class).list())
			.containsExactlyInAnyOrder("id", "instructor_id", "session_hash", "created_at", "last_seen_at",
					"expires_at", "revoked_at");
		assertThat(jdbc.sql("""
				SELECT table_name || '.' || column_name FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND (column_name LIKE '%token%' OR column_name LIKE '%secret%' OR column_name LIKE '%access%'
				       OR column_name LIKE '%refresh%' OR column_name LIKE '%credential%')
				""").query(String.class).list())
			.isEmpty();
	}

	@Test
	void 확인하면_expires_at이_now_14일로_연장되고_instructor_id를_돌려준다() {
		String sessionId = sessions.issue(instructorId);
		jdbc.sql("UPDATE auth_session SET expires_at = now() + interval '1 day'").update();

		assertThat(sessions.touch(sessionId)).contains(instructorId);
		assertThat(jdbc.sql("""
				SELECT abs(extract(epoch FROM expires_at - (now() + interval '14 days'))) < 60
				FROM auth_session
				""")
				.query(Boolean.class)
				.single())
			.isTrue();
	}

	@Test
	void expires_at이_지난_세션은_확인_결과가_비어_있다() {
		String sessionId = sessions.issue(instructorId);
		jdbc.sql("UPDATE auth_session SET expires_at = now() - interval '1 second'").update();

		assertThat(sessions.touch(sessionId)).isEmpty();
	}

	@Test
	void revoke하면_revoked_at이_채워지고_이후_확인_결과가_비어_있다() {
		String sessionId = sessions.issue(instructorId);

		sessions.revoke(sessionId);

		assertThat(jdbc.sql("SELECT revoked_at IS NOT NULL FROM auth_session").query(Boolean.class).single())
			.isTrue();
		assertThat(sessions.touch(sessionId)).isEmpty();
	}

}
