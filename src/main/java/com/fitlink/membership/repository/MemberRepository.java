package com.fitlink.membership.repository;

import com.fitlink.membership.model.Member;
import com.fitlink.membership.model.MemberChange;
import com.fitlink.membership.model.MemberStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {

	private final JdbcClient jdbc;

	public MemberRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// 새 회원은 ACTIVE(컬럼 기본값)
	public long insert(long instructorId, String name, String memo) {
		return jdbc.sql("""
				INSERT INTO member (instructor_id, name, memo) VALUES (:instructorId, :name, :memo) RETURNING id""")
			.param("instructorId", instructorId)
			.param("name", name)
			.param("memo", memo)
			.query(Long.class)
			.single();
	}


	// linkOpened는 살아 있는 링크(revoked_at IS NULL)의 first_opened_at으로만 본다. 끊은 링크를 열었던 기록은 쓰지 않는다
	private static final String SELECT_MEMBER = """
			SELECT m.id, m.name, m.memo, m.status, l.first_opened_at IS NOT NULL AS link_opened
			  FROM member m
			  LEFT JOIN member_link l ON l.member_id = m.id AND l.revoked_at IS NULL
			""";

	// 링크를 열지 않은 ACTIVE → 나머지 ACTIVE → ENDED, 묶음 안은 이름순 · 같은 이름은 먼저 추가한 순.
	// 이름은 DB 정렬 규칙(collation)에 따라 순서가 달라지므로 "C"(코드 포인트 순, 한글 음절은 가나다 순)로 고정한다
	public List<Member> findAll(long instructorId) {
		return jdbc.sql(SELECT_MEMBER + """
				 WHERE m.instructor_id = :instructorId
				 ORDER BY CASE WHEN m.status = 'ENDED' THEN 2
				               WHEN l.first_opened_at IS NULL THEN 0
				               ELSE 1 END,
				          m.name COLLATE "C", m.id
				""").param("instructorId", instructorId).query(MemberRepository::member).list();
	}

	// 다른 강사의 회원도 없는 회원과 같이 빈 값이다
	public Optional<Member> find(long instructorId, long memberId) {
		return jdbc.sql(SELECT_MEMBER + " WHERE m.id = :memberId AND m.instructor_id = :instructorId")
			.param("memberId", memberId)
			.param("instructorId", instructorId)
			.query(MemberRepository::member)
			.optional();
	}

	// 같은 회원의 링크 재발급 · 수강권 등록을 한 줄로 세운다. 회원 키를 바꾸지 않으므로 FOR NO KEY UPDATE로 충분하고,
	// 링크 · 수강권 INSERT의 외래 키 확인(KEY SHARE)과 충돌하지 않는다. 남의 회원 · 없는 회원이면 빈 값
	public Optional<MemberStatus> lock(long instructorId, long memberId) {
		return jdbc
			.sql("SELECT status FROM member WHERE id = :memberId AND instructor_id = :instructorId FOR NO KEY UPDATE")
			.param("memberId", memberId)
			.param("instructorId", instructorId)
			.query(String.class)
			.optional()
			.map(MemberStatus::valueOf);
	}

	// 보낸 필드만 바꾼다. 빈 변경도 행이 있으면 1을 돌려준다. 0이면 남의 회원이거나 없는 회원이다
	public int update(long instructorId, long memberId, MemberChange change) {
		return jdbc.sql("""
				UPDATE member
				   SET name   = coalesce(:name, name),
				       memo   = CASE WHEN :memoChanged THEN :memo ELSE memo END,
				       status = coalesce(:status, status)
				 WHERE id = :memberId AND instructor_id = :instructorId
				""")
			.param("name", change.name())
			.param("memoChanged", change.memoChanged())
			.param("memo", change.memo())
			.param("status", change.status() == null ? null : change.status().name())
			.param("memberId", memberId)
			.param("instructorId", instructorId)
			.update();
	}

	private static Member member(ResultSet rs, int row) throws SQLException {
		return new Member(rs.getLong("id"), rs.getString("name"), rs.getString("memo"),
				MemberStatus.valueOf(rs.getString("status")), rs.getBoolean("link_opened"));
	}

}
