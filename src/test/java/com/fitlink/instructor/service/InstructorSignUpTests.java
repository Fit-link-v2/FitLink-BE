package com.fitlink.instructor.service;

import com.fitlink.IntegrationTest;
import com.fitlink.instructor.InstructorSignUpCommand;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InstructorSignUpTests extends IntegrationTest {

	@Autowired
	InstructorService instructors;

	@Test
	void 처음_보는_카카오_번호는_instructor_1행과_setting_14일_1행을_만든다() {
		long id = instructors.signUpOrFind(new InstructorSignUpCommand(1001L, "김강사", "coach@example.com"));

		assertThat(count("instructor")).isEqualTo(1);
		assertThat(jdbc.sql("SELECT provider, provider_user_id, name, email FROM instructor WHERE id = ?")
				.param(id)
				.query((rs, n) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)))
				.single())
			.containsExactly("KAKAO", "1001", "김강사", "coach@example.com");
		assertThat(jdbc.sql("SELECT open_range_days FROM setting WHERE instructor_id = ?")
				.param(id)
				.query(Integer.class)
				.single())
			.isEqualTo(14);
	}

	@Test
	void 닉네임_이메일이_없어도_가입하고_name_email은_null() {
		long id = instructors.signUpOrFind(new InstructorSignUpCommand(1001L, null, null));

		assertThat(jdbc.sql("SELECT name IS NULL AND email IS NULL FROM instructor WHERE id = ?")
				.param(id)
				.query(Boolean.class)
				.single())
			.isTrue();
		assertThat(count("setting")).isEqualTo(1);
	}

	@Test
	void 같은_번호로_다시_가입_처리하면_같은_id_행_수_그대로() {
		long first = instructors.signUpOrFind(new InstructorSignUpCommand(1001L, "김강사", null));
		long second = instructors.signUpOrFind(new InstructorSignUpCommand(1001L, "김강사", null));

		assertThat(second).isEqualTo(first);
		assertThat(count("instructor")).isEqualTo(1);
		assertThat(count("setting")).isEqualTo(1);
	}

	@Test
	void 같은_번호의_가입_처리_두_개를_동시에_실행해도_instructor_setting_1행씩() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		Callable<Long> signUp = () -> {
			start.await();
			return instructors.signUpOrFind(new InstructorSignUpCommand(1001L, "김강사", null));
		};

		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			Future<Long> a = pool.submit(signUp);
			Future<Long> b = pool.submit(signUp);
			start.countDown();

			assertThat(a.get()).isEqualTo(b.get());
		}
		assertThat(count("instructor")).isEqualTo(1);
		assertThat(count("setting")).isEqualTo(1);
	}

	private long count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
	}

}
