---
id: BE-ADR-0009
status: accepted
date: 2026-10-01
deciders: [BE]
source: [DEC-0004, PRD-0001 AC 3.2.10, AC 3.2.11]
---

# 수강권 종류 겹침은 등록 검사 + EXCLUDE 제약 두 겹으로 막는다

## 맥락

한 회원이 횟수권과 월 정액을 기간이 겹치게 가질 수 없고, 같은 종류끼리는 겹쳐도 된다(DEC-0004). "같은 회원 · 다른 종류 · 기간 겹침"은 일반 UNIQUE로 표현할 수 없다.

## 결정

1. **DB 제약.** `btree_gist` 확장을 켜고 `entitlement`에 EXCLUDE 제약을 건다.

   ```sql
   EXCLUDE USING gist (
     member_id WITH =,
     kind WITH <>,
     (daterange(window_start, window_end, '[]')) WITH &&
   )
   ```

   PostgreSQL 문서 기준으로 `btree_gist`는 `<>` 연산자에 대한 인덱스 지원을 제공하고, 이를 EXCLUDE 제약에 쓸 수 있다. 같은 종류는 `kind WITH <>`가 거짓이라 비교 대상이 아니다

2. **등록 검사.** 월 정액의 뒤쪽 주기는 아직 `entitlement` 행이 없어 제약이 보지 못한다. 그래서 등록할 때 애플리케이션이 `subscription`의 전체 기간과 비교한다. 동시에 두 등록이 들어와도 검사가 서로를 보도록 회원 행을 `FOR UPDATE`로 잠근다

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| 애플리케이션 검사만 | 버그나 동시 요청으로 뚫리면 겹친 데이터가 그대로 들어간다 |
| 트리거 | 제약보다 읽기 어렵고, 같은 일을 선언으로 할 수 있다 |

## 대가

- `btree_gist` 확장이 필요하다. 배포할 DB에서 확장을 켤 수 있는지 확인해야 한다
- 배치가 주기 행을 만들 때 제약에 걸리면 그 INSERT가 실패한다. 배치는 월 정액 하나씩 따로 돌려 실패를 격리한다
