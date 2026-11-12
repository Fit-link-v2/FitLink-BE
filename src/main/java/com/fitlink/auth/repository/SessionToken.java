package com.fitlink.auth.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

// 세션 ID는 쿠키에만 있고 DB에는 hash만 남는다. DB가 유출돼도 세션을 가로챌 수 없다.
// 쿠키 문자열을 그대로 해시하므로 형식이 틀린 값도 에러 없이 "없는 세션"이 된다
final class SessionToken {

	private static final SecureRandom RANDOM = new SecureRandom();

	private SessionToken() {
	}

	// 32바이트 난수를 base64url(패딩 없음) 43자로
	static String generate() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static byte[] hash(String sessionId) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException e) {
			// 모든 Java 구현이 SHA-256을 제공해야 한다(MessageDigest 명세)
			throw new IllegalStateException(e);
		}
	}

}
