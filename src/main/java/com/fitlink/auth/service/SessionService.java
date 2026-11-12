package com.fitlink.auth.service;

import com.fitlink.auth.repository.SessionRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

	private final SessionRepository sessions;

	SessionService(SessionRepository sessions) {
		this.sessions = sessions;
	}

	// 유효한 세션이면 만료를 14일 뒤로 연장하고 강사 id를 돌려준다
	public Optional<Long> authenticate(String sessionId) {
		return sessions.touch(sessionId);
	}

	// 이미 무효인 세션이면 아무것도 바꾸지 않는다
	public void revoke(String sessionId) {
		sessions.revoke(sessionId);
	}

}
