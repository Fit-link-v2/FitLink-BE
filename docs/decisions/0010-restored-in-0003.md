---
id: BE-ADR-0010
status: accepted
date: 2026-10-01
deciders: [BE]
source: [DEC-0006, PRD-0003 US 7]
---

# `booking.restored`는 기능 0003(마이그레이션 002)에서 만든다

## 맥락

`booking.restored`(취소 시 차감을 돌려줬는지)는 원래 PRD 5(취소 마감 규칙)에서 추가할 계획이었다. PRD 5 전에는 취소가 항상 복구라 값이 필요 없었기 때문이다. 그 경우 마이그레이션 004가 "컬럼 추가 → 기존 CANCELED 행 채우기 → CHECK 추가" 세 단계로 나뉜다.

휴강이 PRD 3 US 7로 들어오면서(DEC-0006) PRD 3 시점에 이미 "강사 휴강은 마감과 무관하게 복구"를 DB가 강제해야 한다. 그 제약 `booking_instructor_cancel_restores`가 `restored`를 참조한다.

## 결정

`restored`를 마이그레이션 002(기능 0003)에서 `booking`과 함께 만든다. 기능 0005 전까지는 모든 취소가 `restored = true`다.

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| 계획대로 004에서 추가 | 휴강 규칙을 DB에 못 박지 못한 채 1차 배포가 나가고, 004가 세 단계 마이그레이션이 된다 |

## 대가

- 기능 0005 전까지는 항상 `true`인 컬럼을 저장한다
