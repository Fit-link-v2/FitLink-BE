package com.fitlink.entitlement.repository;

import com.fitlink.entitlement.model.PassRegistration;
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

}
