---
id: BE-ADR-0004
status: accepted
date: 2026-10-01
deciders: [BE]
source: []
---

# 상태 값은 `text` + CHECK로 표현한다

## 맥락

`member.status`, `booking.status`, `waitlist.status`, `entitlement.kind`, `subscription.period_unit` 등 값이 정해진 컬럼이 많다.

## 결정

PostgreSQL `enum` 타입 대신 `text` 컬럼에 `CHECK (x IN (...))`를 건다.

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| PostgreSQL `enum` | 값 삭제 · 순서 변경이 사실상 불가능하다 |
| 코드 테이블 + FK | 값이 몇 개뿐인데 조인과 테이블이 늘어난다 |

## 대가

- 값을 늘리면 CHECK를 교체하는 마이그레이션이 필요하다
