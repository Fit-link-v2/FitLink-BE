package com.fitlink.instructor.application;

import com.fitlink.instructor.infra.SessionRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

	private final SessionRepository sessions;

	SessionService(SessionRepository sessions) {
		this.sessions = sessions;
	}

	// 유효한 세션이면 만료를 연장하고 강사 id를 돌려준다(AC-6 · AC-7)
	public Optional<Long> authenticate(String sessionId) {
		return sessions.touch(sessionId);
	}

}
