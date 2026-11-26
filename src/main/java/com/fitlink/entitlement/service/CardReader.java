package com.fitlink.entitlement.service;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.fitlink.entitlement.model.Card;
import com.fitlink.entitlement.model.CardOrder;
import com.fitlink.entitlement.model.MemberCard;
import com.fitlink.entitlement.repository.CardRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

// 카드 목록과 등록 응답이 이 계산을 쓴다. 그래서 같은 수강권은 어디서 읽어도 같은 카드가 된다
@Service
public class CardReader {

	private final CardRepository cards;

	private final Clock clock;

	CardReader(CardRepository cards, Clock clock) {
		this.cards = cards;
		this.clock = clock;
	}

	// 회원별로 다음에 차감될 순서로 정렬한 카드. 카드가 없는 회원 · 다른 강사의 회원은 결과에 없다
	public Map<Long, List<Card>> cards(long instructorId, Collection<Long> memberIds) {
		return cards(instructorId, memberIds, LocalDate.now(clock));
	}

	// 요청에서 이미 오늘을 구했으면 그 날짜를 넘긴다. 자정 경계에서 검사와 응답의 날짜가 어긋나지 않게 하려는 것이다
	public Map<Long, List<Card>> cards(long instructorId, Collection<Long> memberIds, LocalDate today) {
		if (memberIds.isEmpty()) {
			return Map.of();
		}
		return cards.findByMembers(instructorId, memberIds, today)
			.stream()
			.collect(groupingBy(MemberCard::memberId, collectingAndThen(mapping(MemberCard::card, toList()),
					list -> list.stream().sorted(CardOrder.NEXT_TO_DEDUCT).toList())));
	}

	// 방금 쓴 수강권 한 장의 카드. 같은 트랜잭션에서 부르므로 아직 커밋하지 않은 행도 읽는다
	public <T extends Card> T card(long instructorId, long memberId, Class<T> type, long id, LocalDate today) {
		return cards(instructorId, List.of(memberId), today).getOrDefault(memberId, List.of())
			.stream()
			.filter(card -> type.isInstance(card) && card.id() == id)
			.map(type::cast)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(type.getSimpleName() + " " + id + "의 카드가 없다"));
	}

}
