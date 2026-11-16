package com.fitlink.membership.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

class LinkTokenTests {

	@Test
	void 토큰은_base64url_22자이고_해시는_토큰의_SHA_256_32바이트() throws Exception {
		LinkToken token = LinkToken.generate();

		assertThat(token.raw()).matches("[A-Za-z0-9_-]{22}");
		assertThat(token.hash()).hasSize(32)
			.isEqualTo(MessageDigest.getInstance("SHA-256").digest(token.raw().getBytes(StandardCharsets.US_ASCII)));
	}

	@Test
	void 두_번_만든_토큰이_다르다() {
		assertThat(LinkToken.generate().raw()).isNotEqualTo(LinkToken.generate().raw());
	}

	@Test
	void 문자열로_바꿔도_원본이_나오지_않는다() {
		LinkToken token = LinkToken.generate();

		assertThat(token.toString()).doesNotContain(token.raw());
	}

}
