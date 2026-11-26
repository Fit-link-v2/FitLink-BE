package com.fitlink.entitlement.repository;

import com.fitlink.entitlement.model.CardState;
import com.fitlink.entitlement.model.CurrentPeriod;
import com.fitlink.entitlement.model.MemberCard;
import com.fitlink.entitlement.model.PassCard;
import com.fitlink.entitlement.model.PeriodUnit;
import com.fitlink.entitlement.model.SubscriptionCard;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// 카드는 횟수권 행과 월 정액 등록에서 나온다. 회원이 몇 명이든 쿼리 한 번으로 읽는다
@Repository
public class CardRepository {

	private final JdbcClient jdbc;

	public CardRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// 그 강사의 회원 것만 읽는다. 다른 강사의 회원 id가 섞여 있으면 그 회원의 카드는 없다.
	// 월 정액은 오늘을 덮는 주기 행을 붙인다. 주기는 겹치지 않으므로 많아야 하나다. memberIds는 비어 있으면 안 된다.
	// 맨 앞 주석은 "회원 수와 상관없이 쿼리 한 번"을 테스트가 SQL 로그에서 세는 표시다
	public List<MemberCard> findByMembers(long instructorId, Collection<Long> memberIds, LocalDate today) {
		return jdbc.sql("""
				/* member cards */
				SELECT e.member_id, 'PASS' AS kind, e.id, e.window_start AS starts_on, e.window_end AS ends_on,
				       e.max_count, e.max_count - e.used_count AS remaining,
				       NULL::text AS period_unit, NULL::int AS count_per_period,
				       NULL::bigint AS period_id, NULL::date AS period_start, NULL::date AS period_end,
				       NULL::int AS period_max, NULL::int AS period_remaining
				  FROM entitlement e
				  JOIN member m ON m.id = e.member_id
				 WHERE e.kind = 'PASS' AND m.instructor_id = :instructorId AND e.member_id IN (:memberIds)
				UNION ALL
				SELECT s.member_id, 'SUBSCRIPTION', s.id, s.starts_on, s.ends_on,
				       NULL, NULL,
				       s.period_unit, s.count_per_period,
				       p.id, p.window_start, p.window_end,
				       p.max_count, p.max_count - p.used_count
				  FROM subscription s
				  JOIN member m ON m.id = s.member_id
				  LEFT JOIN entitlement p ON p.source_id = s.id AND p.window_start <= :today AND :today <= p.window_end
				 WHERE m.instructor_id = :instructorId AND s.member_id IN (:memberIds)
				""")
			.param("instructorId", instructorId)
			.param("memberIds", memberIds)
			.param("today", today)
			.query((rs, row) -> new MemberCard(rs.getLong("member_id"),
					"PASS".equals(rs.getString("kind")) ? pass(rs, today) : subscription(rs, today)))
			.list();
	}

	private static PassCard pass(ResultSet rs, LocalDate today) throws SQLException {
		LocalDate startsOn = rs.getObject("starts_on", LocalDate.class);
		LocalDate endsOn = rs.getObject("ends_on", LocalDate.class);
		return new PassCard(rs.getLong("id"), startsOn, endsOn, rs.getInt("max_count"), rs.getInt("remaining"),
				CardState.of(startsOn, endsOn, today));
	}

	private static SubscriptionCard subscription(ResultSet rs, LocalDate today) throws SQLException {
		LocalDate startsOn = rs.getObject("starts_on", LocalDate.class);
		LocalDate endsOn = rs.getObject("ends_on", LocalDate.class);
		CurrentPeriod current = (rs.getObject("period_id") == null) ? null
				: new CurrentPeriod(rs.getLong("period_id"), rs.getObject("period_start", LocalDate.class),
						rs.getObject("period_end", LocalDate.class), rs.getInt("period_max"),
						rs.getInt("period_remaining"));
		return new SubscriptionCard(rs.getLong("id"), PeriodUnit.valueOf(rs.getString("period_unit")),
				rs.getInt("count_per_period"), startsOn, endsOn, CardState.of(startsOn, endsOn, today), current);
	}

}
