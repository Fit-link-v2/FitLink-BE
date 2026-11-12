package com.fitlink.schedule.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitlink.IntegrationTest;
import com.fitlink.schedule.ScheduleFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

// 시각은 테스트 시계 기본값(2026-11-12 목요일 12:00 서울) 기준이다. 오픈 범위 14일이면 11-26은 들어가지 않는다
class SlotGenerationTests extends IntegrationTest {

	static final int MONDAY = 1;

	static final int TUESDAY = 2;

	static final int WEDNESDAY = 3;

	static final int THURSDAY = 4;

	static final LocalDate NOV_17 = LocalDate.of(2026, 11, 17);

	static final LocalDate NOV_24 = LocalDate.of(2026, 11, 24);

	@Autowired
	SlotGeneration generation;

	@Autowired
	Clock clock;

	@Autowired
	TransactionTemplate transaction;

	ScheduleFixtures given;

	long instructorId;

	@BeforeEach
	void 강사() {
		given = new ScheduleFixtures(jdbc);
		instructorId = given.instructor("1001");
	}

	@Test
	void 목요일_12시_14일에_화_20시_규칙이면_화요일_2개가_서울_20시로_정원을_복사해_생긴다() {
		long recurrenceId = given.recurrence(instructorId, TUESDAY, "20:00", 8, true);

		int created = generation.generateFor(instructorId, now());

		assertThat(created).isEqualTo(2);
		assertThat(slots()).containsExactly(
				new Row(recurrenceId, NOV_17, Instant.parse("2026-11-17T11:00:00Z"), 8, false),
				new Row(recurrenceId, NOV_24, Instant.parse("2026-11-24T11:00:00Z"), 8, false));
	}

	@Test
	void DB_세션_타임존이_달라도_서울_시각으로_만든다() {
		given.recurrence(instructorId, TUESDAY, "20:00", 8, true);

		transaction.executeWithoutResult(status -> {
			jdbc.sql("SET LOCAL TIME ZONE 'America/New_York'").update();
			generation.generateFor(instructorId, now());
		});

		assertThat(slots()).extracting(Row::startsAt)
			.containsExactly(Instant.parse("2026-11-17T11:00:00Z"), Instant.parse("2026-11-24T11:00:00Z"));
	}

	@Test
	void 오늘_이미_지난_회차는_만들지_않는다() {
		given.recurrence(instructorId, TUESDAY, "20:00", 8, true);
		testClock.set("2026-11-17T21:00:00+09:00");

		generation.generateFor(instructorId, now());

		assertThat(slots()).extracting(Row::occursOn).containsExactly(NOV_24);
	}

	@Test
	void 시작_시각이_지금과_같은_회차도_만들지_않는다() {
		given.recurrence(instructorId, TUESDAY, "20:00", 8, true);
		testClock.set("2026-11-17T20:00:00+09:00");

		generation.generateFor(instructorId, now());

		assertThat(slots()).extracting(Row::occursOn).containsExactly(NOV_24);
	}

	@Test
	void 오픈_범위_끝_날은_들어가지_않는다() {
		jdbc.sql("UPDATE setting SET open_range_days = 7 WHERE instructor_id = :id").param("id", instructorId).update();
		given.recurrence(instructorId, THURSDAY, "20:00", 8, true);

		generation.generateFor(instructorId, now());

		assertThat(slots()).extracting(Row::occursOn).containsExactly(LocalDate.of(2026, 11, 12));
	}

	@Test
	void 꺼진_규칙은_회차를_만들지_않는다() {
		given.recurrence(instructorId, TUESDAY, "20:00", 8, false);

		assertThat(generation.generateFor(instructorId, now())).isZero();
	}

	@Test
	void 두_번_돌려도_행_수가_같고_휴강_시각을_바꾼_회차가_다시_생기지_않는다() {
		given.recurrence(instructorId, TUESDAY, "20:00", 8, true);
		generation.generateFor(instructorId, now());
		cancel(NOV_17);
		moveTo(NOV_24, "2026-11-24T19:30:00+09:00");

		int created = generation.generateFor(instructorId, now());

		assertThat(created).isZero();
		assertThat(slots()).hasSize(2);
	}

	@Test
	void 강사를_정하면_그_강사의_회차만_만든다() {
		long otherId = given.instructor("2002");
		given.recurrence(instructorId, TUESDAY, "20:00", 8, true);
		given.recurrence(otherId, TUESDAY, "20:00", 8, true);

		generation.generateFor(instructorId, now());

		assertThat(jdbc.sql("SELECT count(*) FROM class_slot WHERE instructor_id = :id")
			.param("id", otherId)
			.query(Integer.class)
			.single()).isZero();
	}

	@Test
	void 전체_실행은_모든_강사의_회차를_만든다() {
		long otherId = given.instructor("2002");
		given.recurrence(instructorId, TUESDAY, "20:00", 8, true);
		given.recurrence(otherId, TUESDAY, "20:00", 8, true);

		assertThat(generation.generateAll(now())).isEqualTo(4);
	}

	@Test
	void 꺼진_규칙의_휴강_시각_변경_회차를_같은_요일_시각의_규칙이_이어받고_새_회차는_없다() {
		long a = given.recurrence(instructorId, TUESDAY, "20:00", 8, false);
		given.slot(instructorId, a, NOV_17.toString(), "2026-11-17T20:00:00+09:00", "2026-11-13T09:00:00+09:00");
		given.slot(instructorId, a, NOV_24.toString(), "2026-11-24T19:30:00+09:00", null);
		long b = given.recurrence(instructorId, TUESDAY, "20:00", 10, true);

		generation.takeOverAndGenerate(List.of(b), instructorId, now());

		assertThat(slots()).containsExactly(
				new Row(b, NOV_17, Instant.parse("2026-11-17T11:00:00Z"), 8, true),
				new Row(b, NOV_24, Instant.parse("2026-11-24T10:30:00Z"), 8, false));
	}

	@Test
	void 이어받기_뒤_새로_열리는_날의_회차는_새_규칙의_정원을_쓴다() {
		long a = given.recurrence(instructorId, TUESDAY, "20:00", 8, false);
		given.slot(instructorId, a, NOV_17.toString(), "2026-11-17T20:00:00+09:00", null);
		long b = given.recurrence(instructorId, TUESDAY, "20:00", 10, true);

		generation.takeOverAndGenerate(List.of(b), instructorId, now());

		assertThat(slots()).containsExactly(
				new Row(b, NOV_17, Instant.parse("2026-11-17T11:00:00Z"), 8, false),
				new Row(b, NOV_24, Instant.parse("2026-11-24T11:00:00Z"), 10, false));
	}

	@Test
	void 어제_이전_회차는_이어받지_않는다() {
		long a = given.recurrence(instructorId, TUESDAY, "20:00", 8, false);
		long past = given.slot(instructorId, a, "2026-11-10", "2026-11-10T20:00:00+09:00", null);
		long b = given.recurrence(instructorId, TUESDAY, "20:00", 10, true);

		generation.takeOverAndGenerate(List.of(b), instructorId, now());

		assertThat(ruleOf(past)).isEqualTo(a);
	}

	@Test
	void 시각이_다른_꺼진_규칙의_회차는_이어받지_않는다() {
		long otherTime = given.recurrence(instructorId, TUESDAY, "19:00", 8, false);
		long slot = given.slot(instructorId, otherTime, NOV_17.toString(), "2026-11-17T19:00:00+09:00", null);
		long b = given.recurrence(instructorId, TUESDAY, "20:00", 10, true);

		generation.takeOverAndGenerate(List.of(b), instructorId, now());

		assertThat(ruleOf(slot)).isEqualTo(otherTime);
	}

	@Test
	void 요일이_다른_꺼진_규칙의_회차는_이어받지_않는다() {
		long otherDay = given.recurrence(instructorId, THURSDAY, "20:00", 8, false);
		long slot = given.slot(instructorId, otherDay, "2026-11-19", "2026-11-19T20:00:00+09:00", null);
		long b = given.recurrence(instructorId, TUESDAY, "20:00", 10, true);

		generation.takeOverAndGenerate(List.of(b), instructorId, now());

		assertThat(ruleOf(slot)).isEqualTo(otherDay);
	}

	@Test
	void 다른_강사의_꺼진_규칙은_이어받지_않는다() {
		long otherId = given.instructor("2002");
		long others = given.recurrence(otherId, TUESDAY, "20:00", 8, false);
		long slot = given.slot(otherId, others, NOV_17.toString(), "2026-11-17T20:00:00+09:00", null);
		long b = given.recurrence(instructorId, TUESDAY, "20:00", 10, true);

		generation.takeOverAndGenerate(List.of(b), instructorId, now());

		assertThat(ruleOf(slot)).isEqualTo(others);
	}

	@Test
	void 다른_강사의_규칙_id를_넘기면_아무것도_옮기지_않는다() {
		long otherId = given.instructor("2002");
		long off = given.recurrence(otherId, TUESDAY, "20:00", 8, false);
		long slot = given.slot(otherId, off, NOV_17.toString(), "2026-11-17T20:00:00+09:00", null);
		long othersActive = given.recurrence(otherId, TUESDAY, "20:00", 10, true);

		generation.takeOverAndGenerate(List.of(othersActive), instructorId, now());

		assertThat(ruleOf(slot)).isEqualTo(off);
	}

	@Test
	void 꺼진_규칙으로는_회차를_옮기지_않는다() {
		long a = given.recurrence(instructorId, TUESDAY, "20:00", 8, false);
		long slot = given.slot(instructorId, a, NOV_17.toString(), "2026-11-17T20:00:00+09:00", null);
		long stillOff = given.recurrence(instructorId, TUESDAY, "20:00", 10, false);

		generation.takeOverAndGenerate(List.of(stillOff), instructorId, now());

		assertThat(ruleOf(slot)).isEqualTo(a);
	}

	// 규칙을 끄는 요청과 자정 실행이 겹치면 꺼진 규칙 둘이 같은 날 회차를 가질 수 있다
	@Test
	void 꺼진_규칙_둘이_같은_날_회차를_가져도_실패하지_않고_한_행만_옮긴다() {
		long a = given.recurrence(instructorId, TUESDAY, "20:00", 8, false);
		long b = given.recurrence(instructorId, TUESDAY, "20:00", 8, false);
		given.slot(instructorId, a, NOV_17.toString(), "2026-11-17T19:30:00+09:00", null);
		given.slot(instructorId, b, NOV_17.toString(), "2026-11-17T20:00:00+09:00", null);
		long c = given.recurrence(instructorId, TUESDAY, "20:00", 10, true);

		generation.takeOverAndGenerate(List.of(c), instructorId, now());

		assertThat(slots()).filteredOn(row -> row.occursOn().equals(NOV_17))
			.extracting(Row::recurrenceId)
			.containsOnlyOnce(c);
	}

	@Test
	void 이미_자기_회차가_있는_날의_꺼진_규칙_회차는_옮기지_않는다() {
		long a = given.recurrence(instructorId, TUESDAY, "20:00", 8, false);
		long moved = given.slot(instructorId, a, NOV_17.toString(), "2026-11-17T19:30:00+09:00", null);
		long b = given.recurrence(instructorId, TUESDAY, "20:00", 10, true);
		given.slot(instructorId, b, NOV_17.toString(), "2026-11-17T20:00:00+09:00", null);

		generation.takeOverAndGenerate(List.of(b), instructorId, now());

		assertThat(ruleOf(moved)).isEqualTo(a);
	}

	// 한 규칙의 회차 생성이 다른 규칙의 이어받기보다 먼저 돌면 그 날 회차가 이미 있어 이어받기가 유니크에 걸린다
	@Test
	void 규칙_여러_개를_함께_켜면_모두_이어받은_뒤_회차를_만든다() {
		long offWednesday = given.recurrence(instructorId, WEDNESDAY, "20:00", 8, false);
		long moved = given.slot(instructorId, offWednesday, "2026-11-18", "2026-11-18T19:30:00+09:00", null);
		long monday = given.recurrence(instructorId, MONDAY, "20:00", 10, true);
		long wednesday = given.recurrence(instructorId, WEDNESDAY, "20:00", 10, true);

		generation.takeOverAndGenerate(List.of(monday, wednesday), instructorId, now());

		assertThat(ruleOf(moved)).isEqualTo(wednesday);
		assertThat(slots()).filteredOn(row -> row.occursOn().equals(LocalDate.of(2026, 11, 18))).hasSize(1);
	}

	@Test
	void 휴강한_일회성_수업_시각에는_나중에_만든_규칙의_회차가_생기지_않는다() {
		given.slot(instructorId, null, NOV_17.toString(), "2026-11-17T20:00:00+09:00", "2026-11-13T09:00:00+09:00");
		long recurrenceId = given.recurrence(instructorId, TUESDAY, "20:00", 8, true);

		generation.generateFor(instructorId, now());

		assertThat(jdbc.sql("SELECT count(*) FROM class_slot WHERE recurrence_id = :id AND occurs_on = :day")
			.param("id", recurrenceId)
			.param("day", NOV_17)
			.query(Integer.class)
			.single()).isZero();
	}

	@Test
	void 휴강_아닌_일회성_수업과_같은_시각이면_회차를_건너뛰고_나머지는_만든다() {
		given.slot(instructorId, null, NOV_17.toString(), "2026-11-17T20:00:00+09:00", null);
		long recurrenceId = given.recurrence(instructorId, TUESDAY, "20:00", 8, true);

		generation.generateFor(instructorId, now());

		assertThat(slots()).extracting(Row::recurrenceId).containsExactly(null, recurrenceId);
	}

	private GenerationTime now() {
		return GenerationTime.at(clock);
	}

	private long ruleOf(long slotId) {
		return jdbc.sql("SELECT recurrence_id FROM class_slot WHERE id = :id").param("id", slotId).query(Long.class).single();
	}

	private void cancel(LocalDate day) {
		jdbc.sql("UPDATE class_slot SET canceled_at = now() WHERE occurs_on = :day").param("day", day).update();
	}

	private void moveTo(LocalDate day, String startsAt) {
		jdbc.sql("UPDATE class_slot SET starts_at = CAST(:startsAt AS timestamptz) WHERE occurs_on = :day")
			.param("startsAt", startsAt)
			.param("day", day)
			.update();
	}

	private List<Row> slots() {
		return jdbc.sql("SELECT recurrence_id, occurs_on, starts_at, capacity, canceled_at FROM class_slot ORDER BY occurs_on, id")
			.query((rs, rowNum) -> new Row(rs.getObject("recurrence_id", Long.class),
					rs.getObject("occurs_on", LocalDate.class),
					rs.getObject("starts_at", OffsetDateTime.class).toInstant(), rs.getInt("capacity"),
					rs.getObject("canceled_at", OffsetDateTime.class) != null))
			.list();
	}

	record Row(Long recurrenceId, LocalDate occursOn, Instant startsAt, int capacity, boolean canceled) {
	}

}
