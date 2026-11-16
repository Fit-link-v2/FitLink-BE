package com.fitlink.membership;

import static com.fitlink.membership.MembershipFixtures.hash;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitlink.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

class MembershipSchemaTests extends IntegrationTest {

	MembershipFixtures given;

	long instructorId;

	@BeforeEach
	void 강사() {
		given = new MembershipFixtures(jdbc);
		instructorId = given.instructor("1001");
	}

	@ParameterizedTest(name = "{0}자")
	@ValueSource(ints = { 0, 51 })
	void 이름이_1_50자_밖이면_member_name_CHECK_위반(int length) {
		assertThatThrownBy(() -> given.member(instructorId, "가".repeat(length), "ACTIVE"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("member_name_check");
	}

	@Test
	void 이름_50자는_된다() {
		assertThatCode(() -> given.member(instructorId, "가".repeat(50), "ACTIVE")).doesNotThrowAnyException();
	}

	@Test
	void 메모가_500자를_넘으면_member_memo_CHECK_위반() {
		long memberId = given.member(instructorId, "김OO", "ACTIVE");

		assertThatCode(() -> setMemo(memberId, "가".repeat(500))).doesNotThrowAnyException();
		assertThatThrownBy(() -> setMemo(memberId, "가".repeat(501)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("member_memo_check");
	}

	@Test
	void 상태는_ACTIVE_ENDED만() {
		assertThatCode(() -> given.member(instructorId, "김OO", "ENDED")).doesNotThrowAnyException();
		assertThatThrownBy(() -> given.member(instructorId, "김OO", "DELETED"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("member_status_check");
	}

	@Test
	void 한_회원에_끊지_않은_링크_두_번째_INSERT는_member_link_active_uidx_위반() {
		long memberId = given.member(instructorId, "김OO", "ACTIVE");
		given.link(memberId, hash(1), null, null);

		assertThatThrownBy(() -> given.link(memberId, hash(2), null, null))
			.isInstanceOf(DuplicateKeyException.class)
			.hasMessageContaining("member_link_active_uidx");
	}

	@Test
	void 끊은_링크는_여러_개여도_된다() {
		long memberId = given.member(instructorId, "김OO", "ACTIVE");
		given.link(memberId, hash(1), "2026-11-10T09:00:00+09:00", null);
		given.link(memberId, hash(2), "2026-11-11T09:00:00+09:00", null);

		assertThatCode(() -> given.link(memberId, hash(3), null, null)).doesNotThrowAnyException();
	}

	@Test
	void 같은_token_hash는_다른_회원이어도_유니크_위반() {
		long first = given.member(instructorId, "김OO", "ACTIVE");
		long second = given.member(given.instructor("2002"), "이OO", "ACTIVE");
		given.link(first, hash(1), "2026-11-10T09:00:00+09:00", null);

		assertThatThrownBy(() -> given.link(second, hash(1), null, null)).isInstanceOf(DuplicateKeyException.class)
			.hasMessageContaining("member_link_token_hash_key");
	}

	@ParameterizedTest(name = "{0}바이트")
	@ValueSource(ints = { 16, 33 })
	void 해시가_32바이트가_아니면_member_link_token_hash_length_위반(int length) {
		long memberId = given.member(instructorId, "김OO", "ACTIVE");

		assertThatThrownBy(() -> given.link(memberId, new byte[length], null, null))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("member_link_token_hash_length");
	}

	// 컬럼을 하나라도 더하면 이 테스트가 깨진다. 토큰 원본을 담는 컬럼이 생기지 않았는지 리뷰에서 다시 보게 하려는 의도다
	@Test
	void 링크_테이블에는_토큰_원본_컬럼이_없다() {
		assertThat(jdbc.sql("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'member_link'
				""").query(String.class).list())
			.containsExactlyInAnyOrder("id", "member_id", "token_hash", "issued_at", "first_opened_at", "last_used_at",
					"revoked_at");
	}

	private void setMemo(long memberId, String memo) {
		jdbc.sql("UPDATE member SET memo = :memo WHERE id = :id").param("memo", memo).param("id", memberId).update();
	}

}
