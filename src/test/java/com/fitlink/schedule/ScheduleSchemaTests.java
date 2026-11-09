package com.fitlink.schedule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitlink.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

class ScheduleSchemaTests extends IntegrationTest {

	static final int TUESDAY = 2;

	ScheduleFixtures given;

	long instructorId;

	@BeforeEach
	void 강사() {
		given = new ScheduleFixtures(jdbc);
		instructorId = given.instructor("1001");
	}

	@Test
	void 같은_recurrence_id_occurs_on_두_번째_INSERT는_class_slot_occurrence_uk_위반() {
		long recurrenceId = given.recurrence(instructorId, TUESDAY, "20:00", 8, true);
		given.slot(instructorId, recurrenceId, "2026-11-17", "2026-11-17T20:00:00+09:00", null);

		assertThatThrownBy(
				() -> given.slot(instructorId, recurrenceId, "2026-11-17", "2026-11-17T19:30:00+09:00", null))
			.isInstanceOf(DuplicateKeyException.class)
			.hasMessageContaining("class_slot_occurrence_uk");
	}

	@Test
	void 같은_강사_같은_시각의_휴강_아닌_슬롯_두_번째_INSERT는_class_slot_time_uidx_위반() {
		given.slot(instructorId, null, "2026-11-17", "2026-11-17T20:00:00+09:00", null);

		assertThatThrownBy(() -> given.slot(instructorId, null, "2026-11-17", "2026-11-17T20:00:00+09:00", null))
			.isInstanceOf(DuplicateKeyException.class)
			.hasMessageContaining("class_slot_time_uidx");
	}

	@Test
	void 휴강한_슬롯과는_같은_시각이어도_된다() {
		given.slot(instructorId, null, "2026-11-17", "2026-11-17T20:00:00+09:00", "2026-11-10T09:00:00+09:00");

		assertThatCode(() -> given.slot(instructorId, null, "2026-11-17", "2026-11-17T20:00:00+09:00", null))
			.doesNotThrowAnyException();
	}

	@Test
	void 같은_강사_요일_시각의_켜진_규칙_두_번째_INSERT는_recurrence_slot_uidx_위반() {
		given.recurrence(instructorId, TUESDAY, "20:00", 8, true);

		assertThatThrownBy(() -> given.recurrence(instructorId, TUESDAY, "20:00", 8, true))
			.isInstanceOf(DuplicateKeyException.class)
			.hasMessageContaining("recurrence_slot_uidx");
	}

	@Test
	void 꺼진_규칙과는_같은_요일_시각이어도_된다() {
		given.recurrence(instructorId, TUESDAY, "20:00", 8, false);

		assertThatCode(() -> given.recurrence(instructorId, TUESDAY, "20:00", 8, true)).doesNotThrowAnyException();
	}

	@Test
	void 다른_강사의_규칙을_가리키는_슬롯은_class_slot_recurrence_owner_fk_위반() {
		long other = given.instructor("2002");
		long othersRecurrence = given.recurrence(other, TUESDAY, "20:00", 8, true);

		assertThatThrownBy(
				() -> given.slot(instructorId, othersRecurrence, "2026-11-17", "2026-11-17T20:00:00+09:00", null))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("class_slot_recurrence_owner_fk");
	}

	@Test
	void 정원보다_많이_찬_슬롯은_class_slot_taken_range_위반() {
		long slotId = given.slot(instructorId, null, "2026-11-17", "2026-11-17T20:00:00+09:00", null);

		assertThatThrownBy(() -> jdbc.sql("UPDATE class_slot SET taken = capacity + 1 WHERE id = :id")
			.param("id", slotId)
			.update()).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("class_slot_taken_range");
	}

}
