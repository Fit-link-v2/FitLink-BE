package com.fitlink.schedule.infra;

import com.fitlink.schedule.application.Recurrence;
import com.fitlink.schedule.application.Weekday;
import java.time.LocalTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RecurrenceRepository {

	private final JdbcClient jdbc;

	public RecurrenceRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// 이 강사의 켜진 규칙 중 같은 시각이고 요일이 겹치는 것의 요일(0 = 일요일)
	public List<Integer> findActiveWeekdays(long instructorId, List<Integer> weekdays, LocalTime startTime) {
		return jdbc.sql("""
				SELECT weekday FROM recurrence
				 WHERE instructor_id = :instructorId AND active
				   AND start_time = :startTime AND weekday IN (:weekdays)
				""")
			.param("instructorId", instructorId)
			.param("startTime", startTime)
			.param("weekdays", weekdays)
			.query(Integer.class)
			.list();
	}

	public long insert(long instructorId, int weekday, LocalTime startTime, int capacity) {
		return jdbc.sql("""
				INSERT INTO recurrence (instructor_id, weekday, start_time, capacity)
				VALUES (:instructorId, :weekday, :startTime, :capacity)
				RETURNING id
				""")
			.param("instructorId", instructorId)
			.param("weekday", weekday)
			.param("startTime", startTime)
			.param("capacity", capacity)
			.query(Long.class)
			.single();
	}

	// 0 = 일요일이라 (weekday + 6) % 7로 월요일을 맨 앞에 둔다
	public List<Recurrence> findAll(long instructorId) {
		return jdbc.sql("""
				SELECT id, weekday, start_time, capacity, active FROM recurrence
				 WHERE instructor_id = :instructorId
				 ORDER BY (weekday + 6) % 7, start_time, id
				""")
			.param("instructorId", instructorId)
			.query((rs, rowNum) -> new Recurrence(rs.getLong("id"), Weekday.ofDow(rs.getInt("weekday")),
					rs.getObject("start_time", LocalTime.class), rs.getInt("capacity"), rs.getBoolean("active")))
			.list();
	}

}
