package com.fitlink.entitlement.repository;

import com.fitlink.entitlement.model.CurrentPeriod;
import com.fitlink.entitlement.model.SubscriptionRegistration;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionRepository {

	private final JdbcClient jdbc;

	public SubscriptionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public long insert(long memberId, SubscriptionRegistration subscription) {
		return jdbc.sql("""
				INSERT INTO subscription (member_id, period_unit, count_per_period, starts_on, period_count, ends_on)
				VALUES (:memberId, :periodUnit, :count, :startsOn, :periodCount, :endsOn)
				RETURNING id""")
			.param("memberId", memberId)
			.param("periodUnit", subscription.per().name())
			.param("count", subscription.count())
			.param("startsOn", subscription.startsOn())
			.param("periodCount", subscription.periodCount())
			.param("endsOn", subscription.endsOn())
			.query(Long.class)
			.single();
	}

	// 월 정액 하나의 주기 중 오늘부터 그 강사의 오픈 범위 [오늘, 오늘 + N일) 끝까지에 걸치는 것을 만든다.
	// 이미 끝난 주기, 등록한 주기 수를 넘는 주기는 만들지 않고, 이미 있는 주기는 건너뛴다(몇 번 돌아도 같다).
	// 회원 · 설정 테이블은 강사의 오픈 범위를 찾으려고 읽기만 한다
	public int generatePeriods(long subscriptionId, LocalDate today) {
		return jdbc.sql("""
				INSERT INTO entitlement (member_id, kind, window_start, window_end, max_count, source_id)
				SELECT s.member_id, 'SUBSCRIPTION', p.start, p.start + u.len - 1, s.count_per_period, s.id
				  FROM subscription s
				  JOIN member  m  ON m.id = s.member_id
				  JOIN setting st ON st.instructor_id = m.instructor_id
				  CROSS JOIN LATERAL (SELECT CASE s.period_unit WHEN 'WEEK' THEN 7 ELSE 28 END AS len) AS u
				  CROSS JOIN LATERAL generate_series(0, s.period_count - 1) AS g(n)
				  CROSS JOIN LATERAL (SELECT s.starts_on + u.len * g.n AS start) AS p
				 WHERE s.id = :subscriptionId
				   AND p.start             <  CAST(:today AS date) + st.open_range_days
				   AND p.start + u.len - 1 >= CAST(:today AS date)
				ON CONFLICT (source_id, window_start) WHERE source_id IS NOT NULL DO NOTHING
				""").param("subscriptionId", subscriptionId).param("today", today).update();
	}

	// 오늘을 덮는 주기. 주기는 겹치지 않으므로 많아야 하나다
	public Optional<CurrentPeriod> currentPeriod(long subscriptionId, LocalDate today) {
		return jdbc.sql("""
				SELECT id, window_start, window_end, max_count, max_count - used_count AS remaining
				  FROM entitlement
				 WHERE source_id = :subscriptionId AND window_start <= :today AND :today <= window_end
				""")
			.param("subscriptionId", subscriptionId)
			.param("today", today)
			.query((rs, row) -> new CurrentPeriod(rs.getLong("id"), rs.getObject("window_start", LocalDate.class),
					rs.getObject("window_end", LocalDate.class), rs.getInt("max_count"), rs.getInt("remaining")))
			.optional();
	}

}
