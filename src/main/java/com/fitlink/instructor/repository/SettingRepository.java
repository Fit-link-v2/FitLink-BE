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

}
