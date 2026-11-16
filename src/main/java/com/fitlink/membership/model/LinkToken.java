package com.fitlink.membership.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

// 회원 개인 링크 /m/{raw}의 토큰. 원본은 발급 응답에만 싣고 DB에는 hash만 남긴다.
// 시간순으로 정렬되는 값(UUID v1 · v7 등)이 아니라 순수 난수다
public record LinkToken(String raw, byte[] hash) {

	private static final SecureRandom RANDOM = new SecureRandom();

	// 16바이트 난수를 base64url(패딩 없음) 22자로
	public static LinkToken generate() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return new LinkToken(raw, sha256(raw));
	}

	private static byte[] sha256(String raw) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException e) {
			// 모든 Java 구현이 SHA-256을 제공해야 한다(MessageDigest 명세)
			throw new IllegalStateException(e);
		}
	}

	// 로그 · 예외 메시지에 원본이 찍히지 않게 한다
	@Override
	public String toString() {
		return "LinkToken[raw=***]";
	}

}
