package com.fitlink.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitlink.IntegrationTest;
import com.fitlink.common.exception.FitlinkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class MembershipFacadeTests extends IntegrationTest {

	@Autowired
	MembershipFacade facade;

	@Autowired
	TransactionTemplate tx;

	MembershipFixtures given;

	long instructorId;

	@BeforeEach
	void 강사() {
		given = new MembershipFixtures(jdbc);
		instructorId = given.instructor("1001");
	}

	@Test
	void 회원_잠금은_ACTIVE면_true_ENDED면_false다() {
		long active = given.member(instructorId, "김OO", "ACTIVE");
		long ended = given.member(instructorId, "박OO", "ENDED");

		Boolean activeResult = tx.execute(status -> facade.lockActive(instructorId, active));
		Boolean endedResult = tx.execute(status -> facade.lockActive(instructorId, ended));

		assertThat(activeResult).isTrue();
		assertThat(endedResult).isFalse();
	}

	@Test
	void 회원_잠금은_다른_강사의_회원이면_NOT_FOUND다() {
		long othersMember = given.member(given.instructor("1002"), "이OO", "ACTIVE");

		assertThatThrownBy(() -> tx.execute(status -> facade.lockActive(instructorId, othersMember)))
			.isInstanceOf(FitlinkException.class)
			.extracting(e -> ((FitlinkException) e).errorCode().code())
			.isEqualTo("NOT_FOUND");
	}

	// 트랜잭션 밖에서 잠그면 문장이 끝나자마자 풀려 아무것도 줄 세우지 못한다. 그런 호출은 예외로 막는다
	@Test
	void 회원_잠금은_트랜잭션_밖에서_부르면_예외다() {
		long active = given.member(instructorId, "김OO", "ACTIVE");

		assertThatThrownBy(() -> facade.lockActive(instructorId, active))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

}
