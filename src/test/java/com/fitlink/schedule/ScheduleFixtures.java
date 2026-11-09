package com.fitlink.schedule;

import org.springframework.jdbc.core.simple.JdbcClient;

// 시간표 테스트의 준비 데이터를 SQL로 바로 넣는다. 만드는 API를 거치지 않아 준비 단계가 테스트 대상에 기대지 않는다
public final class ScheduleFixtures {

	private final JdbcClient jdbc;

	public ScheduleFixtures(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// 강사와 설정 1행. 오픈 범위는 기본값 14일
	public long instructor(String kakaoId) {
		long id = jdbc.sql("INSERT INTO instructor (provider, provider_user_id) VALUES ('KAKAO', :kakaoId) RETURNING id")
			.param("kakaoId", kakaoId)
			.query(Long.class)
			.single();
		jdbc.sql("INSERT INTO setting (instructor_id) VALUES (:id)").param("id", id).update();
		return id;
	}

	// weekday는 0 = 일요일
	public long recurrence(long instructorId, int weekday, String startTime, int capacity, boolean active) {
		return jdbc
			.sql("""
					INSERT INTO recurrence (instructor_id, weekday, start_time, capacity, active)
					VALUES (:instructorId, :weekday, CAST(:startTime AS time), :capacity, :active) RETURNING id""")
			.param("instructorId", instructorId)
			.param("weekday", weekday)
			.param("startTime", startTime)
			.param("capacity", capacity)
			.param("active", active)
			.query(Long.class)
			.single();
	}

	// recurrenceId가 null이면 일회성 수업, canceledAt이 null이 아니면 휴강한 슬롯
	public long slot(long instructorId, Long recurrenceId, String occursOn, String startsAt, String canceledAt) {
		return jdbc
			.sql("""
					INSERT INTO class_slot (instructor_id, recurrence_id, occurs_on, starts_at, capacity, canceled_at)
					VALUES (:instructorId, :recurrenceId, CAST(:occursOn AS date), CAST(:startsAt AS timestamptz), 8,
					        CAST(:canceledAt AS timestamptz))
					RETURNING id""")
			.param("instructorId", instructorId)
			.param("recurrenceId", recurrenceId)
			.param("occursOn", occursOn)
			.param("startsAt", startsAt)
			.param("canceledAt", canceledAt)
			.query(Long.class)
			.single();
	}

}
