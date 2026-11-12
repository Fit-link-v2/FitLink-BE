package com.fitlink.schedule.infra;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRepository {

	private final JdbcClient jdbc;

	public SlotRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// instructorId가 null이면 모든 강사. 오픈 범위는 오늘부터 N일이고 끝 날짜는 넣지 않는다.
	// 날짜 + 시각은 타임존이 없는 값이라 AT TIME ZONE이 서울 시각으로 읽어 순간으로 바꾼다.
	// NOT EXISTS는 휴강한 행까지 봐서, 강사가 휴강한 시각을 회차로 다시 채우지 않는다.
	// 유니크가 둘(회차 · 같은 시각)이라 ON CONFLICT에 대상을 적지 않는다.
	// ORDER BY는 동시 실행끼리 같은 순서로 넣어 deadlock을 피한다
	public int generate(Long instructorId, LocalDate today, OffsetDateTime now) {
		return jdbc.sql("""
				INSERT INTO class_slot (instructor_id, recurrence_id, occurs_on, starts_at, capacity)
				SELECT r.instructor_id, r.id, d.day, d.starts_at, r.capacity
				  FROM recurrence r
				  JOIN setting st ON st.instructor_id = r.instructor_id
				  CROSS JOIN LATERAL generate_series(0, st.open_range_days - 1) AS g(n)
				  CROSS JOIN LATERAL (
				         SELECT CAST(:today AS date) + g.n AS day,
				                (CAST(:today AS date) + g.n + r.start_time) AT TIME ZONE 'Asia/Seoul' AS starts_at
				       ) AS d
				 WHERE r.active
				   AND (CAST(:instructorId AS bigint) IS NULL OR r.instructor_id = :instructorId)
				   AND extract(dow FROM d.day) = r.weekday
				   AND d.starts_at > CAST(:now AS timestamptz)
				   AND NOT EXISTS (SELECT 1 FROM class_slot c
				                    WHERE c.instructor_id = r.instructor_id AND c.starts_at = d.starts_at)
				 ORDER BY r.id, d.day
				ON CONFLICT DO NOTHING
				""")
			.param("instructorId", instructorId)
			.param("today", today)
			.param("now", now)
			.update();
	}

}
