package com.fitlink.auth.service;

import com.fitlink.auth.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 만료된 세션 행을 매일 0시(서울)에 지운다. 만료된 세션은 이미 로그인에 쓰이지 않으므로, 표가 계속 커지지 않게 하려는 것이다.
// 로그아웃했지만 만료 전인 행은 남긴다. 실패하면 로그만 남기고 다음 날 다시 지운다
@Component
class SessionCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);

	private final SessionRepository sessions;

	SessionCleanupJob(SessionRepository sessions) {
		this.sessions = sessions;
	}

	@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
	void daily() {
		try {
			log.info("만료 세션 삭제: {}개", sessions.deleteExpired());
		}
		catch (RuntimeException e) {
			log.error("만료 세션 삭제 실패. 다음 날 다시 지운다", e);
		}
	}

}
