package com.fitlink.instructor.infra;

import com.fitlink.instructor.application.InstructorProfile;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InstructorRepository {

	private final JdbcClient jdbc;

	public InstructorRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// 같은 (provider, provider_user_id)가 있으면 넣지 않고 비어 있는 결과를 돌려준다.
	// 동시에 들어와도 제약이 한 행만 남긴다(prd 3.3 가입)
	public Optional<Long> insertIfAbsent(String provider, String providerUserId, String name, String email) {
		return jdbc.sql("""
				INSERT INTO instructor (provider, provider_user_id, name, email)
				VALUES (:provider, :providerUserId, :name, :email)
				ON CONFLICT ON CONSTRAINT instructor_identity_uk DO NOTHING
				RETURNING id
				""")
			.param("provider", provider)
			.param("providerUserId", providerUserId)
			.param("name", name)
			.param("email", email)
			.query(Long.class)
			.optional();
	}

	public long findIdByIdentity(String provider, String providerUserId) {
		return jdbc.sql("SELECT id FROM instructor WHERE provider = :provider AND provider_user_id = :providerUserId")
			.param("provider", provider)
			.param("providerUserId", providerUserId)
			.query(Long.class)
			.single();
	}

	// 공개 범위 등은 테이블 DEFAULT를 쓴다
	public void insertDefaultSetting(long instructorId) {
		jdbc.sql("INSERT INTO setting (instructor_id) VALUES (:instructorId)")
			.param("instructorId", instructorId)
			.update();
	}

	public Optional<InstructorProfile> findProfile(long instructorId) {
		return jdbc.sql("SELECT id, name, email FROM instructor WHERE id = :id")
			.param("id", instructorId)
			.query(InstructorProfile.class)
			.optional();
	}

}
