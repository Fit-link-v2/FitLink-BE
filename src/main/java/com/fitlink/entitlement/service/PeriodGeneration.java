package com.fitlink.entitlement.service;

import com.fitlink.entitlement.repository.SubscriptionRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 월 정액의 주기별 수강권을 그 강사의 오픈 범위 끝까지 만든다. 몇 번 불러도 같은 주기는 한 번만 생긴다.
 * <p>
 * 여러 건을 만들 때는 한 건씩 따로 되돌릴 수 있게 돈다. 한 문장으로 모두 만들면 한 건의 종류 겹침 위반이 그 문장 전체를
 * 실패시키기 때문이다(ON CONFLICT는 지정한 유니크 인덱스 충돌만 받아 준다). 실패한 건은 로그만 남기고, 아직 끝나지 않은
 * 월 정액은 다음 자정 작업이 다시 채운다
 */
@Service
public class PeriodGeneration {

	private static final Logger log = LoggerFactory.getLogger(PeriodGeneration.class);

	private final SubscriptionRepository subscriptions;

	// 자정 · 앱 시작: 건마다 새 트랜잭션
	private final TransactionTemplate eachInNewTransaction;

	// 오픈 범위 변경: 그 요청의 트랜잭션 안에서 건마다 savepoint
	private final TransactionTemplate eachInSavepoint;

	PeriodGeneration(SubscriptionRepository subscriptions, PlatformTransactionManager transactions) {
		this.subscriptions = subscriptions;
		this.eachInNewTransaction = new TransactionTemplate(transactions);
		this.eachInNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.eachInSavepoint = new TransactionTemplate(transactions);
		this.eachInSavepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
	}

	// 등록 직후. 부르는 쪽의 트랜잭션에 합류하고, 실패하면 등록도 되돌아간다
	public void generate(long subscriptionId, LocalDate today) {
		subscriptions.generatePeriods(subscriptionId, today);
	}

	// 모든 강사의 끝나지 않은 월 정액. 새로 만든 주기 수를 돌려준다
	public int generateAll(LocalDate today) {
		return generateEach(subscriptions.unfinished(null, today), today, eachInNewTransaction);
	}

	// 오픈 범위를 늘린 강사의 끝나지 않은 월 정액. 한 건이 실패하면 그 건만 savepoint로 되돌리므로
	// 부르는 쪽의 트랜잭션(오픈 범위 변경 · 회차 생성)은 그대로 커밋된다
	public int generateFor(long instructorId, LocalDate today) {
		return generateEach(subscriptions.unfinished(instructorId, today), today, eachInSavepoint);
	}

	private int generateEach(Iterable<Long> subscriptionIds, LocalDate today, TransactionTemplate each) {
		int created = 0;
		for (long subscriptionId : subscriptionIds) {
			try {
				created += each.execute(status -> subscriptions.generatePeriods(subscriptionId, today));
			}
			catch (RuntimeException e) {
				log.error("월 정액 {} 주기 생성 실패. 다음 자정 작업이 다시 채운다", subscriptionId, e);
			}
		}
		return created;
	}

}
