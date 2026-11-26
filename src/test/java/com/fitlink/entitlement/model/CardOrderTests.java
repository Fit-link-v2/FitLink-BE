package com.fitlink.entitlement.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

// 등록 규칙 때문에 한 회원의 만료되지 않은 카드는 한 종류뿐이고, 주기 행끼리는 DB 제약이 기간 겹침을 막는다.
// 그래서 날짜가 같은 횟수권 · 월 정액의 순서는 정상 데이터로 만들기 어려워 카드끼리 비교해 확인한다. 오늘은 2026-11-12다
class CardOrderTests {

	static final LocalDate TODAY = LocalDate.parse("2026-11-12");

	@Test
	void 끝_날짜가_같으면_id와_상관없이_횟수권이_먼저다() {
		Card subscription = subscription(1, "2026-11-05", "2026-12-30", period("2026-11-12", "2026-11-18", 1));
		Card pass = pass(9, "2026-11-01", "2026-11-18", 3);

		assertThat(sorted(subscription, pass)).containsExactly(pass, subscription);
	}

	@Test
	void 잔여_0_묶음에서도_끝_날짜가_같으면_횟수권이_먼저다() {
		Card subscription = subscription(1, "2026-11-05", "2026-12-30", period("2026-11-12", "2026-11-18", 0));
		Card pass = pass(9, "2026-11-01", "2026-11-18", 0);

		assertThat(sorted(subscription, pass)).containsExactly(pass, subscription);
	}

	@Test
	void 시작_전_묶음에서_시작일이_같으면_횟수권이_먼저다() {
		Card subscription = subscription(1, "2026-11-20", "2026-12-17", null);
		Card pass = pass(9, "2026-11-20", "2026-12-31", 10);

		assertThat(sorted(subscription, pass)).containsExactly(pass, subscription);
	}

	@Test
	void 만료_묶음에서_종료일이_같으면_횟수권이_먼저다() {
		Card subscription = subscription(1, "2026-10-14", "2026-11-10", null);
		Card pass = pass(9, "2026-10-01", "2026-11-10", 10);

		assertThat(sorted(subscription, pass)).containsExactly(pass, subscription);
	}

	@Test
	void 같은_종류에_같은_날짜면_id_순이다() {
		Card later = pass(5, "2026-11-01", "2026-11-30", 3);
		Card earlier = pass(3, "2026-11-01", "2026-11-30", 3);

		assertThat(sorted(later, earlier)).containsExactly(earlier, later);
	}

	private static List<Card> sorted(Card... cards) {
		return List.of(cards).stream().sorted(CardOrder.NEXT_TO_DEDUCT).toList();
	}

	private static PassCard pass(long id, String startsOn, String endsOn, int remaining) {
		LocalDate start = LocalDate.parse(startsOn);
		LocalDate end = LocalDate.parse(endsOn);
		return new PassCard(id, start, end, 10, remaining, CardState.of(start, end, TODAY));
	}

	private static SubscriptionCard subscription(long id, String startsOn, String endsOn, CurrentPeriod current) {
		LocalDate start = LocalDate.parse(startsOn);
		LocalDate end = LocalDate.parse(endsOn);
		return new SubscriptionCard(id, PeriodUnit.WEEK, 2, start, end, CardState.of(start, end, TODAY), current);
	}

	private static CurrentPeriod period(String startsOn, String endsOn, int remaining) {
		return new CurrentPeriod(100, LocalDate.parse(startsOn), LocalDate.parse(endsOn), 2, remaining);
	}

}
