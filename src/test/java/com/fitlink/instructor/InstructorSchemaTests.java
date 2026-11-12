package com.fitlink.instructor;

import com.fitlink.IntegrationTest;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class InstructorSchemaTests extends IntegrationTest {

	@Test
	void 같은_provider_provider_user_id_두_번째_INSERT는_instructor_identity_uk_위반() {
		String insert = "INSERT INTO instructor (provider, provider_user_id) VALUES ('KAKAO', '1001')";
		jdbc.sql(insert).update();

		assertThatThrownBy(() -> jdbc.sql(insert).update())
				.isInstanceOf(DuplicateKeyException.class)
				.hasMessageContaining("instructor_identity_uk");
	}

}
