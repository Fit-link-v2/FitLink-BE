package com.fitlink.schedule.infra;

import com.fitlink.schedule.application.Slot;
import com.fitlink.schedule.application.Weekday;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRepository {

	// 일회성 수업은 규칙이 없어 LEFT JOIN이다. 규칙의 instructor_id가 아니라 슬롯의 것으로 거른다
	private static final String SELECT_SLOT = """
			SELECT c.id, c.starts_at, c.capacity, c.taken, c.canceled_at,
			       r.id AS rule_id, r.weekday AS rule_weekday, r.start_time AS rule_start_time
			  FROM class_slot c
			  LEFT JOIN recurrence r ON r.id = c.recurrence_id
			""";

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

	// 같은 강사 · 요일 · 시각의 꺼진 규칙이 가진 오늘 이후 회차를 이 규칙의 회차로 바꾼다.
	// 시각 · 휴강 · 정원은 그대로 두고, 회차의 소속만 옮겨 다음 생성이 그 날을 건너뛰게 한다.
	// 규칙 끄기와 자정 실행이 겹치면 한 날에 옮길 회차가 둘일 수 있어, 날짜마다 한 행만 옮겨 회차 유니크에 걸리지 않게 한다
	public void takeOver(long recurrenceId, long instructorId, LocalDate today) {
		jdbc.sql("""
				UPDATE class_slot SET recurrence_id = :recurrenceId
				 WHERE id IN (
				       SELECT DISTINCT ON (c.occurs_on) c.id
				         FROM class_slot c
				         JOIN recurrence old_rule ON old_rule.id = c.recurrence_id
				         JOIN recurrence new_rule
				           ON new_rule.instructor_id = old_rule.instructor_id
				          AND new_rule.weekday = old_rule.weekday
				          AND new_rule.start_time = old_rule.start_time
				        WHERE new_rule.id = :recurrenceId
				          AND new_rule.instructor_id = :instructorId
				          AND new_rule.active
				          AND NOT old_rule.active
				          AND c.occurs_on >= :today
				          AND NOT EXISTS (SELECT 1 FROM class_slot own
				                           WHERE own.recurrence_id = new_rule.id AND own.occurs_on = c.occurs_on)
				        ORDER BY c.occurs_on, c.id DESC
				       )
				""")
			.param("recurrenceId", recurrenceId)
			.param("instructorId", instructorId)
			.param("today", today)
			.update();
	}

	// 시작 시각이 now 이후인 이 강사의 슬롯. 휴강 · 오픈 범위 밖도 넣는다. class_slot_owner_idx를 탄다
	public List<Slot> findUpcoming(long instructorId, OffsetDateTime now) {
		return jdbc.sql(SELECT_SLOT + """
				 WHERE c.instructor_id = :instructorId AND c.starts_at > :now
				 ORDER BY c.starts_at, c.id
				""")
			.param("instructorId", instructorId)
			.param("now", now)
			.query(SlotRepository::slot)
			.list();
	}

	public Optional<Slot> find(long instructorId, long id) {
		return jdbc.sql(SELECT_SLOT + " WHERE c.id = :id AND c.instructor_id = :instructorId")
			.param("id", id)
			.param("instructorId", instructorId)
			.query(SlotRepository::slot)
			.optional();
	}

	// 예약이 있어도 휴강한다. 행은 지우지 않아 회차 생성이 그 날을 다시 채우지 않는다. 바꿨으면 true
	public boolean cancel(long instructorId, long id, OffsetDateTime now) {
		return jdbc.sql("""
				UPDATE class_slot SET canceled_at = :now
				 WHERE id = :id AND instructor_id = :instructorId
				   AND canceled_at IS NULL AND starts_at > :now
				""")
			.param("id", id)
			.param("instructorId", instructorId)
			.param("now", now)
			.update() == 1;
	}

	private static Slot slot(ResultSet rs, int rowNum) throws SQLException {
		long ruleId = rs.getLong("rule_id");
		Slot.Rule rule = rs.wasNull() ? null
				: new Slot.Rule(ruleId, Weekday.ofDow(rs.getInt("rule_weekday")),
						rs.getObject("rule_start_time", LocalTime.class));
		return new Slot(rs.getLong("id"), rs.getObject("starts_at", OffsetDateTime.class), rs.getInt("capacity"),
				rs.getInt("taken"), rs.getObject("canceled_at", OffsetDateTime.class), rule);
	}

}
