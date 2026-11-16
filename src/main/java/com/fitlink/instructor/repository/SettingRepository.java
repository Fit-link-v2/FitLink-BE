package com.fitlink.instructor.repository;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SettingRepository {

	private final JdbcClient jdbc;

	public SettingRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Integer> findOpenRangeDays(long instructorId) {
		return jdbc.sql("SELECT open_range_days FROM setting WHERE instructor_id = :instructorId")
			.param("instructorId", instructorId)
			.query(Integer.class)
			.optional();
	}

	// 바꾸기 전 값을 읽으며 행을 잠근다. 같은 강사의 변경이 동시에 오면 차례로 돌아, "늘었는지"를 바로 앞 변경의 결과와 비교한다
	public Optional<Integer> lockOpenRangeDays(long instructorId) {
		return jdbc.sql("SELECT open_range_days FROM setting WHERE instructor_id = :instructorId FOR UPDATE")
			.param("instructorId", instructorId)
			.query(Integer.class)
			.optional();
	}

	public void updateOpenRangeDays(long instructorId, int openRangeDays) {
		jdbc.sql("UPDATE setting SET open_range_days = :openRangeDays WHERE instructor_id = :instructorId")
			.param("openRangeDays", openRangeDays)
			.param("instructorId", instructorId)
			.update();
	}

}
