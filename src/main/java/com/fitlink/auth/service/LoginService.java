package com.fitlink.auth.service;

import com.fitlink.auth.client.KakaoOAuthClient;
import com.fitlink.auth.repository.SessionRepository;
import com.fitlink.instructor.model.KakaoUser;
import com.fitlink.instructor.service.InstructorService;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LoginService {

	private final KakaoOAuthClient kakao;

	private final InstructorService instructors;

	private final SessionRepository sessions;

	private final TransactionTemplate transaction;

	LoginService(KakaoOAuthClient kakao, InstructorService instructors, SessionRepository sessions,
			TransactionTemplate transaction) {
		this.kakao = kakao;
		this.instructors = instructors;
		this.sessions = sessions;
		this.transaction = transaction;
	}

	// 카카오 사용자로 가입 또는 조회하고 새 세션 ID를 돌려준다. 요청에 이전 세션이 있으면 무효로 한다(한 브라우저에 세션 하나).
	// 카카오 호출은 트랜잭션 밖에서 해 응답을 기다리는 동안 DB 연결을 잡지 않는다
	public String login(String code, Optional<String> previousSessionId) {
		KakaoUser user = kakao.fetchUser(code);
		return transaction.execute(status -> {
			long instructorId = instructors.signUpOrFind(user);
			previousSessionId.ifPresent(sessions::revoke);
			return sessions.issue(instructorId);
		});
	}

}
