package com.fitlink.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitlink.IntegrationTest;
import com.fitlink.auth.repository.SessionRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

// 세션 만료는 DB의 now()로 계산하므로 expires_at도 DB 시각 기준으로 만든다
class SessionCleanupJobTests extends IntegrationTest {

	@Autowired
	SessionCleanupJob job;

	@Autowired
	SessionRepository sessions;

	@Test
	void 만료된_세션만_지우고_지운_세션의_쿠키는_401() throws Exception {
		long instructorId = jdbc
			.sql("INSERT INTO instructor (provider, provider_user_id) VALUES ('KAKAO', '1001') RETURNING id")
			.query(Long.class)
			.single();
		String expired = sessions.issue(instructorId); // id 1. 테스트마다 id를 1부터 다시 센다
		String loggedOut = sessions.issue(instructorId); // id 2
		String valid = sessions.issue(instructorId); // id 3
		jdbc.sql("UPDATE auth_session SET expires_at = now() - interval '1 second' WHERE id = 1").update();
		sessions.revoke(loggedOut);

		job.daily();

		assertThat(remainingIds()).containsExactly(2L, 3L);
		// 행이 지워진 뒤에도 그 쿠키로는 로그인되지 않고, 남은 로그아웃 행 · 유효한 행은 원래대로 동작한다
		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", expired))).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", loggedOut))).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/me").cookie(new Cookie("SESSION", valid))).andExpect(status().isOk());
	}

	@Test
	void 정리_작업은_서울_기준_매일_0시에_돈다() throws Exception {
		Scheduled scheduled = SessionCleanupJob.class.getDeclaredMethod("daily").getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}

	private List<Long> remainingIds() {
		return jdbc.sql("SELECT id FROM auth_session ORDER BY id").query(Long.class).list();
	}

}
