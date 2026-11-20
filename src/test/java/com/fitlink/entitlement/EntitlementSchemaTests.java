package com.fitlink.entitlement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitlink.IntegrationTest;
import com.fitlink.membership.MembershipFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

class EntitlementSchemaTests extends IntegrationTest {

	EntitlementFixtures given;

	MembershipFixtures membership;

	long instructorId;

	long memberId;

	@BeforeEach
	void 강사와_회원() {
		given = new EntitlementFixtures(jdbc);
		membership = new MembershipFixtures(jdbc);
		instructorId = membership.instructor("1001");
		memberId = membership.member(instructorId, "김OO", "ACTIVE");
	}

	// 종류 겹침

	@Test
	void 같은_회원의_횟수권과_월_정액_주기가_겹치면_entitlement_kind_no_overlap_위반() {
		long subscriptionId = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		given.pass(memberId, "2026-11-01", "2026-11-30", 10);

		assertThatThrownBy(() -> given.period(subscriptionId, memberId, "2026-11-12", "2026-11-18", 2))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("entitlement_kind_no_overlap");
	}

	@Test
	void 월_정액_주기가_먼저_있어도_겹치는_횟수권은_entitlement_kind_no_overlap_위반() {
		long subscriptionId = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		given.period(subscriptionId, memberId, "2026-11-12", "2026-11-18", 2);

		assertThatThrownBy(() -> given.pass(memberId, "2026-11-18", "2026-12-31", 10))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("entitlement_kind_no_overlap");
	}

	@Test
	void 횟수권_종료일과_월_정액_주기_시작일이_같은_날이면_겹침이라_위반() {
		long subscriptionId = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		given.pass(memberId, "2026-11-01", "2026-11-12", 10);

		assertThatThrownBy(() -> given.period(subscriptionId, memberId, "2026-11-12", "2026-11-18", 2))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("entitlement_kind_no_overlap");
	}

	@Test
	void 횟수권이_월_정액_주기_시작_전날에_끝나면_된다() {
		long subscriptionId = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		given.pass(memberId, "2026-11-01", "2026-11-11", 10);

		assertThatCode(() -> given.period(subscriptionId, memberId, "2026-11-12", "2026-11-18", 2))
			.doesNotThrowAnyException();
	}

	@Test
	void 같은_회원의_횟수권끼리는_기간이_겹쳐도_된다() {
		given.pass(memberId, "2026-11-01", "2026-11-30", 10);

		assertThatCode(() -> given.pass(memberId, "2026-11-15", "2026-12-15", 10)).doesNotThrowAnyException();
	}

	@Test
	void 같은_회원의_월_정액_주기끼리는_기간이_겹쳐도_된다() {
		long first = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		long second = given.subscription(memberId, "FOUR_WEEKS", 8, "2026-11-09", 2);
		given.period(first, memberId, "2026-11-12", "2026-11-18", 2);

		assertThatCode(() -> given.period(second, memberId, "2026-11-09", "2026-12-06", 8)).doesNotThrowAnyException();
	}

	@Test
	void 다른_회원이면_다른_종류끼리_기간이_겹쳐도_된다() {
		long otherMemberId = membership.member(instructorId, "이OO", "ACTIVE");
		long subscriptionId = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		given.pass(otherMemberId, "2026-11-01", "2026-11-30", 10);

		assertThatCode(() -> given.period(subscriptionId, memberId, "2026-11-12", "2026-11-18", 2))
			.doesNotThrowAnyException();
	}

	// 주기 중복

	@Test
	void 같은_source_id_window_start_두_번째_INSERT는_entitlement_period_uidx_위반() {
		long subscriptionId = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		given.period(subscriptionId, memberId, "2026-11-12", "2026-11-18", 2);

		assertThatThrownBy(() -> given.period(subscriptionId, memberId, "2026-11-12", "2026-11-18", 2))
			.isInstanceOf(DuplicateKeyException.class)
			.hasMessageContaining("entitlement_period_uidx");
	}

	@Test
	void 같은_월_정액의_다른_주기는_된다() {
		long subscriptionId = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		given.period(subscriptionId, memberId, "2026-11-05", "2026-11-11", 2);

		assertThatCode(() -> given.period(subscriptionId, memberId, "2026-11-12", "2026-11-18", 2))
			.doesNotThrowAnyException();
	}

	@Test
	void 다른_월_정액이면_같은_시작일이어도_된다() {
		long first = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		long second = given.subscription(memberId, "WEEK", 3, "2026-11-05", 4);
		given.period(first, memberId, "2026-11-12", "2026-11-18", 2);

		assertThatCode(() -> given.period(second, memberId, "2026-11-12", "2026-11-18", 3)).doesNotThrowAnyException();
	}

	@Test
	void 횟수권은_같은_시작일로_여러_장이어도_된다() {
		given.pass(memberId, "2026-11-01", "2026-11-30", 10);

		assertThatCode(() -> given.pass(memberId, "2026-11-01", "2026-11-30", 10)).doesNotThrowAnyException();
	}

	// 수강권 행 CHECK

	@ParameterizedTest(name = "{0}회")
	@ValueSource(ints = { 0, 1000 })
	void 최대_횟수가_1_999_밖이면_entitlement_max_count_CHECK_위반(int maxCount) {
		assertThatThrownBy(() -> given.pass(memberId, "2026-11-01", "2026-11-30", maxCount))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("entitlement_max_count_check");
	}

	@ParameterizedTest(name = "{0}회")
	@ValueSource(ints = { 1, 999 })
	void 최대_횟수_1과_999는_된다(int maxCount) {
		assertThatCode(() -> given.pass(memberId, "2026-11-01", "2026-11-30", maxCount)).doesNotThrowAnyException();
	}

	@ParameterizedTest(name = "사용 {0} / 최대 10")
	@ValueSource(ints = { -1, 11 })
	void 사용_횟수가_0_최대_횟수_밖이면_entitlement_used_range_위반(int usedCount) {
		assertThatThrownBy(() -> given.entitlement(memberId, "PASS", "2026-11-01", "2026-11-30", 10, usedCount, null))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("entitlement_used_range");
	}

	@ParameterizedTest(name = "사용 {0} / 최대 10")
	@ValueSource(ints = { 0, 10 })
	void 사용_횟수_0과_최대_횟수는_된다(int usedCount) {
		assertThatCode(() -> given.entitlement(memberId, "PASS", "2026-11-01", "2026-11-30", 10, usedCount, null))
			.doesNotThrowAnyException();
	}

	@Test
	void 시작일이_종료일보다_늦으면_entitlement_window_order_위반() {
		assertThatThrownBy(() -> given.pass(memberId, "2026-11-30", "2026-11-29", 10))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("entitlement_window_order");
	}

	@Test
	void 시작일과_종료일이_같은_하루짜리는_된다() {
		assertThatCode(() -> given.pass(memberId, "2026-11-30", "2026-11-30", 10)).doesNotThrowAnyException();
	}

	@Test
	void 월_정액_행인데_등록을_가리키지_않으면_entitlement_source_shape_위반() {
		assertThatThrownBy(
				() -> given.entitlement(memberId, "SUBSCRIPTION", "2026-11-12", "2026-11-18", 2, 0, null))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("entitlement_source_shape");
	}

	@Test
	void 횟수권_행인데_등록을_가리키면_entitlement_source_shape_위반() {
		long subscriptionId = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);

		assertThatThrownBy(
				() -> given.entitlement(memberId, "PASS", "2026-11-01", "2026-11-30", 10, 0, subscriptionId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("entitlement_source_shape");
	}

	@Test
	void 수강권_종류는_PASS_SUBSCRIPTION만() {
		assertThatThrownBy(() -> given.entitlement(memberId, "MONTHLY", "2026-11-01", "2026-11-30", 10, 0, null))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("entitlement_kind_check");
	}

	// 월 정액 등록 CHECK

	@ParameterizedTest(name = "주기당 {0}회")
	@ValueSource(ints = { 0, 100 })
	void 주기당_횟수가_1_99_밖이면_subscription_count_per_period_CHECK_위반(int countPerPeriod) {
		assertThatThrownBy(() -> given.subscription(memberId, "WEEK", countPerPeriod, "2026-11-05", 8))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("subscription_count_per_period_check");
	}

	@ParameterizedTest(name = "주기당 {0}회")
	@ValueSource(ints = { 1, 99 })
	void 주기당_횟수_1과_99는_된다(int countPerPeriod) {
		assertThatCode(() -> given.subscription(memberId, "WEEK", countPerPeriod, "2026-11-05", 8))
			.doesNotThrowAnyException();
	}

	// 종료일을 규칙대로 맞춰(시작일 − 1일) 주기 수 조건만 어긋나게 한다
	@Test
	void 주기_수가_0이면_subscription_period_count_CHECK_위반() {
		assertThatThrownBy(() -> given.subscription(memberId, "FOUR_WEEKS", 2, "2026-11-05", 0))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("subscription_period_count_check");
	}

	@ParameterizedTest(name = "WEEK {0}주기")
	@ValueSource(ints = { 1, 6, 10 })
	void 주당_방식인데_주기_수가_4의_배수가_아니면_subscription_four_week_multiple_위반(int periodCount) {
		assertThatThrownBy(() -> given.subscription(memberId, "WEEK", 2, "2026-11-05", periodCount))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("subscription_four_week_multiple");
	}

	@ParameterizedTest(name = "{0} {1}주기")
	@CsvSource({ "WEEK, 4", "WEEK, 12", "FOUR_WEEKS, 1", "FOUR_WEEKS, 3" })
	void 주당_방식은_4의_배수_4주_방식은_아무_주기_수나_된다(String periodUnit, int periodCount) {
		assertThatCode(() -> given.subscription(memberId, periodUnit, 2, "2026-11-05", periodCount))
			.doesNotThrowAnyException();
	}

	@ParameterizedTest(name = "{0} {1}주기 종료 {2}")
	@CsvSource({ "WEEK, 8, 2026-12-30", "FOUR_WEEKS, 2, 2026-12-30" })
	void 종료일이_시작일_더하기_주기_길이_곱하기_주기_수_빼기_1일이면_된다(String periodUnit, int periodCount, String endsOn) {
		assertThatCode(() -> given.subscription(memberId, periodUnit, 2, "2026-11-05", periodCount, endsOn))
			.doesNotThrowAnyException();
	}

	// 4주 방식에 7일 주기로 센 종료일(11-18)도 어긋난 것으로 본다
	@ParameterizedTest(name = "{0} {1}주기 종료 {2}")
	@CsvSource({ "WEEK, 8, 2026-12-29", "WEEK, 8, 2026-12-31", "FOUR_WEEKS, 2, 2026-12-31",
			"FOUR_WEEKS, 2, 2026-11-18" })
	void 종료일이_주기로_계산한_날과_다르면_subscription_end_위반(String periodUnit, int periodCount, String endsOn) {
		assertThatThrownBy(() -> given.subscription(memberId, periodUnit, 2, "2026-11-05", periodCount, endsOn))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("subscription_end");
	}

	// 종료일이 주기로 정해지므로 기간은 7일 단위로만 늘어난다. 48주(335일 차이)가 되는 마지막 길이다
	@ParameterizedTest(name = "{0} {1}주기")
	@CsvSource({ "WEEK, 48", "FOUR_WEEKS, 12" })
	void 등록_기간_48주는_된다(String periodUnit, int periodCount) {
		assertThatCode(() -> given.subscription(memberId, periodUnit, 2, "2026-11-05", periodCount))
			.doesNotThrowAnyException();
	}

	@ParameterizedTest(name = "{0} {1}주기")
	@CsvSource({ "WEEK, 52", "FOUR_WEEKS, 13" })
	void 등록_기간이_48주를_넘으면_subscription_max_length_위반(String periodUnit, int periodCount) {
		assertThatThrownBy(() -> given.subscription(memberId, periodUnit, 2, "2026-11-05", periodCount))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("subscription_max_length");
	}

	@Test
	void 주기_단위는_WEEK_FOUR_WEEKS만() {
		assertThatThrownBy(() -> given.subscription(memberId, "MONTH", 2, "2026-11-05", 1))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("subscription_period_unit_check");
	}

}
