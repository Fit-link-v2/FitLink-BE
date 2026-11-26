package com.fitlink.entitlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitlink.IntegrationTest;
import com.fitlink.entitlement.EntitlementCardInfo.Pass;
import com.fitlink.entitlement.EntitlementCardInfo.Period;
import com.fitlink.entitlement.EntitlementCardInfo.Subscription;
import com.fitlink.membership.MembershipFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

// 오늘은 테스트 시계의 기본값 2026-11-12(목)이다
class EntitlementFacadeTests extends IntegrationTest {

	@Autowired
	EntitlementFacade facade;

	MembershipFixtures members;

	EntitlementFixtures given;

	long instructorId;

	long memberId;

	@BeforeEach
	void 강사와_회원() {
		members = new MembershipFixtures(jdbc);
		given = new EntitlementFixtures(jdbc);
		instructorId = members.instructor("1001");
		memberId = members.member(instructorId, "김OO", "ACTIVE");
	}

	@Test
	void 횟수권_카드는_남은_횟수와_오늘_기준_상태를_준다() {
		long id = pass(memberId, "2026-11-01", "2026-11-30", 10, 3);

		assertThat(cardsOf(memberId)).containsExactly(new Pass(id, d("2026-11-01"), d("2026-11-30"), 10, 7, "ACTIVE"));
	}

	@ParameterizedTest(name = "{0} ~ {1} → {2}")
	@CsvSource({ "2026-11-13, 2026-11-30, NOT_STARTED", "2026-11-12, 2026-11-30, ACTIVE",
			"2026-11-01, 2026-11-12, ACTIVE", "2026-11-01, 2026-11-11, EXPIRED" })
	void 횟수권_상태는_시작일과_종료일로_정한다(String startsOn, String endsOn, String state) {
		pass(memberId, startsOn, endsOn, 10, 0);

		assertThat(cardsOf(memberId)).singleElement().extracting(EntitlementCardInfo::state).isEqualTo(state);
	}

	@Test
	void 잔여_0인_횟수권도_ACTIVE다() {
		long id = pass(memberId, "2026-11-01", "2026-11-30", 5, 5);

		assertThat(cardsOf(memberId)).containsExactly(new Pass(id, d("2026-11-01"), d("2026-11-30"), 5, 0, "ACTIVE"));
	}

	@Test
	void 월_정액_카드는_등록_하나가_카드_하나이고_오늘을_덮는_주기를_currentPeriod로_준다() {
		long id = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		period(id, "2026-11-05", "2026-11-11", 2);
		long current = period(id, "2026-11-12", "2026-11-18", 1);
		period(id, "2026-11-19", "2026-11-25", 0);

		assertThat(cardsOf(memberId)).containsExactly(new Subscription(id, "WEEK", 2, d("2026-11-05"),
				d("2026-12-30"), "ACTIVE", new Period(current, d("2026-11-12"), d("2026-11-18"), 2, 1)));
	}

	// 지난 주기 행만 있다. 오늘(11-12)을 덮는 주기는 아직 만들지 않았다
	@Test
	void 오늘을_덮는_주기_행이_없으면_currentPeriod는_null이다() {
		long id = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		period(id, "2026-11-05", "2026-11-11", 2);

		assertThat(cardsOf(memberId)).containsExactly(
				new Subscription(id, "WEEK", 2, d("2026-11-05"), d("2026-12-30"), "ACTIVE", null));
	}

	// 시작 전 · 끝난 월 정액의 상태는 주기가 아니라 등록 전체 기간으로 정한다
	@ParameterizedTest(name = "{0}부터 4주 → {1}")
	@CsvSource({ "2026-11-13, NOT_STARTED", "2026-10-16, ACTIVE", "2026-10-15, EXPIRED" })
	void 월_정액_상태는_등록_전체_기간으로_정한다(String startsOn, String state) {
		given.subscription(memberId, "WEEK", 2, startsOn, 4);

		assertThat(cardsOf(memberId)).singleElement().extracting(EntitlementCardInfo::state).isEqualTo(state);
	}

	// 횟수권과 월 정액 주기 행은 기간이 겹칠 수 없으므로, 월 정액은 주기 행이 없는 것만 섞는다
	@Test
	void 카드는_다음에_차감될_순서로_준다() {
		long pA = pass(memberId, "2026-11-01", "2026-11-30", 10, 0);
		long pB = pass(memberId, "2026-11-01", "2026-11-20", 5, 0);
		long pC = pass(memberId, "2026-11-01", "2026-11-20", 5, 2);
		long pD = pass(memberId, "2026-11-01", "2026-11-15", 3, 3);
		long sE = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		long pF = pass(memberId, "2026-11-20", "2026-12-31", 10, 0);
		long sG = given.subscription(memberId, "WEEK", 2, "2026-11-19", 4);
		long pH = pass(memberId, "2026-10-01", "2026-10-31", 10, 0);
		long pI = pass(memberId, "2026-10-01", "2026-11-10", 10, 0);
		long sJ = given.subscription(memberId, "WEEK", 2, "2026-09-03", 4);

		assertThat(keys(cardsOf(memberId))).containsExactly("P" + pB, "P" + pC, "P" + pA, "P" + pD, "S" + sE,
				"S" + sG, "P" + pF, "P" + pI, "P" + pH, "S" + sJ);
	}

	// 지금 주기의 끝은 11-18 · 12-09, 등록 종료일은 12-30 · 12-09다. 등록 종료일로 줄을 세우면 앞의 둘이 뒤집힌다.
	// 잔여 0인 지금 주기는 차감할 수 없어 뒤로 간다. fixture의 마지막 인자는 주 수가 아니라 주기 수다
	@Test
	void 월_정액은_지금_주기의_끝_날짜로_줄을_서고_지금_주기_잔여가_0이면_뒤로_간다() {
		long weekly = given.subscription(memberId, "WEEK", 2, "2026-11-05", 8);
		period(weekly, "2026-11-12", "2026-11-18", 1);
		long fourWeeks = given.subscription(memberId, "FOUR_WEEKS", 8, "2026-11-12", 1);
		period(fourWeeks, "2026-11-12", "2026-12-09", 5);
		long usedUp = given.subscription(memberId, "WEEK", 2, "2026-11-12", 4);
		period(usedUp, "2026-11-12", "2026-11-18", 0);

		assertThat(cardsOf(memberId)).extracting(EntitlementCardInfo::id).containsExactly(weekly, fourWeeks, usedUp);
	}

	// 지금 주기가 없는 월 정액은 등록 종료일(12-02)로, 잔여 0인 횟수권(12-31)과 같은 묶음에서 줄을 선다
	@Test
	void 잔여_0_묶음에서_지금_주기가_없는_월_정액은_등록_종료일로_줄을_선다() {
		long usedUp = pass(memberId, "2026-11-01", "2026-12-31", 5, 5);
		long noPeriod = given.subscription(memberId, "WEEK", 2, "2026-11-05", 4);

		assertThat(keys(cardsOf(memberId))).containsExactly("S" + noPeriod, "P" + usedUp);
	}

	@Test
	void 요청한_회원의_카드만_회원별로_나눠_주고_다른_강사_회원의_카드는_주지_않는다() {
		long second = members.member(instructorId, "이OO", "ENDED");
		long othersMember = members.member(members.instructor("1002"), "박OO", "ACTIVE");
		long notAsked = members.member(instructorId, "최OO", "ACTIVE");
		long mine = pass(memberId, "2026-11-01", "2026-11-30", 10, 0);
		long secondsPass = pass(second, "2026-11-01", "2026-11-30", 10, 0);
		pass(notAsked, "2026-11-01", "2026-11-30", 10, 0);
		given.subscription(notAsked, "WEEK", 2, "2026-11-05", 8);
		pass(othersMember, "2026-11-01", "2026-11-30", 10, 0);
		given.subscription(othersMember, "WEEK", 2, "2026-11-05", 8);

		Map<Long, List<EntitlementCardInfo>> cards = facade.cards(instructorId, List.of(memberId, second, othersMember));

		assertThat(cards).containsOnlyKeys(memberId, second);
		assertThat(cards.get(memberId)).extracting(EntitlementCardInfo::id).containsExactly(mine);
		assertThat(cards.get(second)).extracting(EntitlementCardInfo::id).containsExactly(secondsPass);
	}

	@Test
	void 회원_id가_없으면_빈_결과다() {
		pass(memberId, "2026-11-01", "2026-11-30", 10, 0);

		assertThat(facade.cards(instructorId, List.of())).isEmpty();
	}

	// 횟수권 id와 월 정액 id는 다른 테이블의 번호라 겹칠 수 있어 종류를 붙인다
	private static List<String> keys(List<EntitlementCardInfo> cards) {
		return cards.stream().map(card -> (card instanceof Pass ? "P" : "S") + card.id()).toList();
	}

	private List<EntitlementCardInfo> cardsOf(long id) {
		return facade.cards(instructorId, List.of(id)).getOrDefault(id, List.of());
	}

	private long pass(long member, String startsOn, String endsOn, int maxCount, int usedCount) {
		return given.entitlement(member, "PASS", startsOn, endsOn, maxCount, usedCount, null);
	}

	// 주당 2회 · 4주마다 8회 주기. remaining을 정하려고 used_count를 넣는다
	private long period(long subscriptionId, String windowStart, String windowEnd, int remaining) {
		Map<String, Object> s = jdbc.sql("SELECT member_id, count_per_period FROM subscription WHERE id = :id")
			.param("id", subscriptionId)
			.query()
			.singleRow();
		int max = (Integer) s.get("count_per_period");
		return given.entitlement((Long) s.get("member_id"), "SUBSCRIPTION", windowStart, windowEnd, max,
				max - remaining, subscriptionId);
	}

	private static LocalDate d(String date) {
		return LocalDate.parse(date);
	}

}
