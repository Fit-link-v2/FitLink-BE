package com.fitlink.membership.service;

import com.fitlink.membership.config.MembershipProperties;
import com.fitlink.membership.model.AddedMember;
import com.fitlink.membership.model.IssuedLink;
import com.fitlink.membership.model.LinkToken;
import com.fitlink.membership.model.Member;
import com.fitlink.membership.model.MemberStatus;
import com.fitlink.membership.repository.MemberLinkRepository;
import com.fitlink.membership.repository.MemberRepository;
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
		LinkToken token = LinkToken.generate();
		links.insert(memberId, token.hash());
		return new AddedMember(new Member(memberId, name, memo, MemberStatus.ACTIVE, false),
				new IssuedLink(properties.linkBaseUrl() + "/m/" + token.raw()));
	}

}
