package com.fitlink.entitlement;

import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;

// 수강권 테스트의 준비 데이터를 SQL로 바로 넣는다. 강사 · 회원은 MembershipFixtures로 만든다.
// 등록 API를 거치지 않아 준비 단계가 테스트 대상에 기대지 않는다
public final class EntitlementFixtures {

	private final JdbcClient jdbc;

	public EntitlementFixtures(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// 월 정액 등록. 종료일은 시작일 + 주기 길이 × 주기 수 − 1일로 계산해 넣는다
	public long subscription(long memberId, String periodUnit, int countPerPeriod, String startsOn, int periodCount) {
		return subscription(memberId, periodUnit, countPerPeriod, startsOn, periodCount,
				endsOn(periodUnit, startsOn, periodCount));
	}

	// 종료일을 직접 정한다. 종료일 규칙을 일부러 어기는 테스트가 쓴다
	public long subscription(long memberId, String periodUnit, int countPerPeriod, String startsOn, int periodCount,
			String endsOn) {
		return jdbc.sql("""
				INSERT INTO subscription (member_id, period_unit, count_per_period, starts_on, period_count, ends_on)
				VALUES (:memberId, :periodUnit, :countPerPeriod, CAST(:startsOn AS date), :periodCount,
				        CAST(:endsOn AS date))
				RETURNING id""")
			.param("memberId", memberId)
			.param("periodUnit", periodUnit)
			.param("countPerPeriod", countPerPeriod)
			.param("startsOn", startsOn)
			.param("periodCount", periodCount)
			.param("endsOn", endsOn)
			.query(Long.class)
			.single();
	}

	// 횟수권 1장. window가 유효 기간 전체다
	public long pass(long memberId, String windowStart, String windowEnd, int maxCount) {
		return entitlement(memberId, "PASS", windowStart, windowEnd, maxCount, 0, null);
	}

	// 월 정액의 한 주기. window가 그 주기다
	public long period(long subscriptionId, long memberId, String windowStart, String windowEnd, int maxCount) {
		return entitlement(memberId, "SUBSCRIPTION", windowStart, windowEnd, maxCount, 0, subscriptionId);
	}

	// used_count를 넣지 않는다. 컬럼 기본값을 확인하는 테스트가 쓴다
	public long passWithoutUsedCount(long memberId, String windowStart, String windowEnd, int maxCount) {
		return jdbc.sql("""
				INSERT INTO entitlement (member_id, kind, window_start, window_end, max_count)
				VALUES (:memberId, 'PASS', CAST(:windowStart AS date), CAST(:windowEnd AS date), :maxCount)
				RETURNING id""")
			.param("memberId", memberId)
			.param("windowStart", windowStart)
			.param("windowEnd", windowEnd)
			.param("maxCount", maxCount)
			.query(Long.class)
			.single();
	}

	// 모든 컬럼을 직접 정한다. 제약을 일부러 어기는 테스트가 쓴다
	public long entitlement(long memberId, String kind, String windowStart, String windowEnd, int maxCount,
			int usedCount, Long sourceId) {
		return jdbc.sql("""
				INSERT INTO entitlement (member_id, kind, window_start, window_end, max_count, used_count, source_id)
				VALUES (:memberId, :kind, CAST(:windowStart AS date), CAST(:windowEnd AS date), :maxCount, :usedCount,
				        :sourceId)
				RETURNING id""")
			.param("memberId", memberId)
			.param("kind", kind)
			.param("windowStart", windowStart)
			.param("windowEnd", windowEnd)
			.param("maxCount", maxCount)
			.param("usedCount", usedCount)
			.param("sourceId", sourceId)
			.query(Long.class)
			.single();
	}

	public static String endsOn(String periodUnit, String startsOn, int periodCount) {
		int periodDays = "WEEK".equals(periodUnit) ? 7 : 28;
		return LocalDate.parse(startsOn).plusDays((long) periodDays * periodCount - 1).toString();
	}

}
