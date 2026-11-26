package com.fitlink.entitlement.repository;

import com.fitlink.entitlement.model.OtherKind;
import com.fitlink.entitlement.model.PassRegistration;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EntitlementRepository {

	private final JdbcClient jdbc;

	public EntitlementRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// 횟수권은 등록 1건에 행 1개이고 window가 유효 기간 전체다. 사용 횟수는 0(컬럼 기본값)으로 시작한다
	public long insertPass(long memberId, PassRegistration pass) {
		return jdbc.sql("""
				INSERT INTO entitlement (member_id, kind, window_start, window_end, max_count)
				VALUES (:memberId, 'PASS', :startsOn, :endsOn, :maxCount)
				RETURNING id""")
			.param("memberId", memberId)
			.param("startsOn", pass.startsOn())
			.param("endsOn", pass.endsOn())
			.param("maxCount", pass.maxCount())
			.query(Long.class)
			.single();
	}

	// 월 정액을 등록하려 할 때 그 회원의 횟수권을 본다. 만료는 종료일이 오늘 앞인 것이라 오늘 끝나는 것 · 시작 전인 것은 살아 있다
	public OtherKind passesAgainst(long memberId, LocalDate startsOn, LocalDate endsOn, LocalDate today) {
		return jdbc.sql("""
				SELECT max(window_end) FILTER (WHERE window_end >= :today) AS active_until,
				       coalesce(bool_or(daterange(window_start, window_end, '[]')
				                        && daterange(CAST(:startsOn AS date), CAST(:endsOn AS date), '[]')), false) AS overlaps
				  FROM entitlement
				 WHERE member_id = :memberId AND kind = 'PASS'
				""")
			.param("today", today)
			.param("startsOn", startsOn)
			.param("endsOn", endsOn)
			.param("memberId", memberId)
			.query((rs, row) -> new OtherKind(rs.getObject("active_until", LocalDate.class), rs.getBoolean("overlaps")))
			.single();
	}

}
