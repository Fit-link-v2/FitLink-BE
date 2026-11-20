package com.fitlink.membership.service;

import com.fitlink.common.exception.CommonErrorCode;
import com.fitlink.common.exception.FitlinkException;
import com.fitlink.membership.config.MembershipProperties;
import com.fitlink.membership.model.AddedMember;
import com.fitlink.membership.model.IssuedLink;
import com.fitlink.membership.model.LinkToken;
import com.fitlink.membership.model.Member;
import com.fitlink.membership.model.MemberChange;
import com.fitlink.membership.model.MemberStatus;
import com.fitlink.membership.repository.MemberLinkRepository;
import com.fitlink.membership.repository.MemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

	private final MemberRepository members;

	private final MemberLinkRepository links;

	private final MembershipProperties properties;

	MemberService(MemberRepository members, MemberLinkRepository links, MembershipProperties properties) {
		this.members = members;
		this.links = links;
		this.properties = properties;
	}

	// 회원과 링크를 한 트랜잭션에서 만든다. 링크 저장이 실패하면 회원도 남지 않는다
	@Transactional
	public AddedMember add(long instructorId, String name, String memo) {
		long memberId = members.insert(instructorId, name, memo);
		return new AddedMember(new Member(memberId, name, memo, MemberStatus.ACTIVE, false), issue(memberId));
	}

	// 이전 링크를 끊고 새 링크를 만든다. 수강 종료(ENDED) 회원도 된다. 유출된 링크를 끊는 수단이라
	// 다시 활성화하지 않고 바로 할 수 있어야 한다. 회원 행을 먼저 잠가 같은 회원의 재발급이 동시에 와도 차례로 돈다
	@Transactional
	public IssuedLink reissue(long instructorId, long memberId) {
		if (!members.lock(instructorId, memberId)) {
			throw notFound();
		}
		links.revokeLive(memberId);
		return issue(memberId);
	}

	private IssuedLink issue(long memberId) {
		LinkToken token = LinkToken.generate();
		links.insert(memberId, token.hash());
		return new IssuedLink(properties.linkBaseUrl() + "/m/" + token.raw());
	}

	@Transactional(readOnly = true)
	public List<Member> list(long instructorId) {
		return members.findAll(instructorId);
	}

	@Transactional(readOnly = true)
	public Member get(long instructorId, long memberId) {
		return members.find(instructorId, memberId).orElseThrow(MemberService::notFound);
	}

	@Transactional
	public Member change(long instructorId, long memberId, MemberChange change) {
		if (members.update(instructorId, memberId, change) == 0) {
			throw notFound();
		}
		return get(instructorId, memberId);
	}

	private static FitlinkException notFound() {
		return new FitlinkException(CommonErrorCode.NOT_FOUND);
	}

}
