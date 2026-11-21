package com.fitlink.bff.service;

import com.fitlink.bff.model.InstructorMember;
import com.fitlink.membership.MembershipFacade;
import com.fitlink.membership.MembershipMemberInfo;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// 강사 화면의 회원 목록 · 상세를 모은다. 여러 Facade를 부르게 되어도 같은 시점의 데이터를 보도록
// 메서드 하나를 읽기 전용 REPEATABLE READ 트랜잭션 하나로 묶는다
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class InstructorMemberView {

	private final MembershipFacade membership;

	InstructorMemberView(MembershipFacade membership) {
		this.membership = membership;
	}

	// 순서는 membership이 정한 회원 목록 순서 그대로다
	public List<InstructorMember> members(long instructorId) {
		return membership.members(instructorId).stream().map(InstructorMemberView::member).toList();
	}

	public InstructorMember member(long instructorId, long memberId) {
		return member(membership.member(instructorId, memberId));
	}

	private static InstructorMember member(MembershipMemberInfo info) {
		return new InstructorMember(info.id(), info.name(), info.memo(), info.status(), info.linkOpened(),
				info.unopened());
	}

}
