package com.fitlink.schedule.repository;

import com.fitlink.schedule.model.Recurrence;
import com.fitlink.schedule.model.Weekday;
import java.time.LocalTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
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

	// 이 강사의 규칙 한 행을 잠가 같은 규칙을 동시에 켜고 끄는 요청이 차례로 돌게 한다.
	// active만 바꾸므로 FOR NO KEY UPDATE다. FOR UPDATE는 회차 INSERT의 외래 키 확인(KEY SHARE)과 충돌해
	// 자정 생성 작업과 서로 기다리다 deadlock이 날 수 있다
	public Optional<Recurrence> findForUpdate(long instructorId, long id) {
		return jdbc.sql("""
				SELECT id, weekday, start_time, capacity, active FROM recurrence
				 WHERE id = :id AND instructor_id = :instructorId
				   FOR NO KEY UPDATE
				""")
			.param("id", id)
			.param("instructorId", instructorId)
			.query(RecurrenceRepository::row)
			.optional();
	}

	public void updateActive(long id, boolean active) {
		jdbc.sql("UPDATE recurrence SET active = :active WHERE id = :id")
			.param("id", id)
			.param("active", active)
			.update();
	}

	// 0 = 일요일이라 (weekday + 6) % 7로 월요일을 맨 앞에 둔다
	public List<Recurrence> findAll(long instructorId) {
		return jdbc.sql("""
				SELECT id, weekday, start_time, capacity, active FROM recurrence
				 WHERE instructor_id = :instructorId
				 ORDER BY (weekday + 6) % 7, start_time, id
				""")
			.param("instructorId", instructorId)
			.query(RecurrenceRepository::row)
			.list();
	}

	private static Recurrence row(ResultSet rs, int rowNum) throws SQLException {
		return new Recurrence(rs.getLong("id"), Weekday.ofDow(rs.getInt("weekday")),
				rs.getObject("start_time", LocalTime.class), rs.getInt("capacity"), rs.getBoolean("active"));
	}

}
