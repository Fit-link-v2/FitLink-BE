package com.fitlink.instructor.infra;

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
	void BE_0001_AC_4_발급한_세션_ID는_32바이트_난수이고_DB에는_SHA_256_해시만_저장된다() throws Exception {
		String sessionId = sessions.issue(instructorId);
		String other = sessions.issue(instructorId);

		assertThat(Base64.getUrlDecoder().decode(sessionId)).hasSize(32);
		assertThat(other).isNotEqualTo(sessionId);

		byte[] expected = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.US_ASCII));
		assertThat(jdbc.sql("SELECT session_hash FROM instructor_session WHERE instructor_id = ? ORDER BY id")
				.param(instructorId)
				.query(byte[].class)
				.list())
			.first()
			.isEqualTo(expected);
	}

	@Test
	void BE_0001_AC_7_확인하면_expires_at이_now_14일로_연장되고_instructor_id를_돌려준다() {
		String sessionId = sessions.issue(instructorId);
		jdbc.sql("UPDATE instructor_session SET expires_at = now() + interval '1 day'").update();

		assertThat(sessions.touch(sessionId)).contains(instructorId);
		assertThat(jdbc.sql("""
				SELECT abs(extract(epoch FROM expires_at - (now() + interval '14 days'))) < 60
				FROM instructor_session
				""")
				.query(Boolean.class)
				.single())
			.isTrue();
	}

	@Test
	void BE_0001_AC_7_expires_at이_지난_세션은_확인_결과가_비어_있다() {
		String sessionId = sessions.issue(instructorId);
		jdbc.sql("UPDATE instructor_session SET expires_at = now() - interval '1 second'").update();

		assertThat(sessions.touch(sessionId)).isEmpty();
	}

	@Test
	void BE_0001_AC_8_revoke하면_revoked_at이_채워지고_이후_확인_결과가_비어_있다() {
		String sessionId = sessions.issue(instructorId);

		sessions.revoke(sessionId);

		assertThat(jdbc.sql("SELECT revoked_at IS NOT NULL FROM instructor_session").query(Boolean.class).single())
			.isTrue();
		assertThat(sessions.touch(sessionId)).isEmpty();
	}

}
