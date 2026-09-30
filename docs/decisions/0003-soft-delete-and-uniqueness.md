---
id: BE-ADR-0003
status: accepted
date: 2026-10-01
deciders: [BE]
source: [PRD-0001 AC 2.1.3, AC 3.3.1, AC 4.3.1, PRD-0003 AC 3.1, PRD-0004 AC 1.1.4]
---

# 지우지 않고 상태로 남기고, 유니크는 부분 인덱스로 건다

## 맥락

회원 종료 · 예약 취소 · 대기 취소 · 링크 재발급 · 휴강은 모두 "없어지는" 동작이다. 행을 지우면 지난 기록과 분쟁 대응 근거가 사라진다.

## 결정

- 행을 지우지 않는다. `status`, `canceled_at`, `revoked_at`으로 남긴다
- 그래서 일반 UNIQUE를 걸 수 없는 곳은 **부분 유니크 인덱스**로 건다

| 인덱스 | 막는 것 |
|---|---|
| `member_link (member_id) WHERE revoked_at IS NULL` | 회원 1명에게 살아 있는 링크 2개 |
| `booking (member_id, class_slot_id) WHERE status = 'ACTIVE'` | 같은 수업 중복 예약 (409 ALREADY_BOOKED) |
| `waitlist (member_id, class_slot_id) WHERE status = 'WAITING'` | 같은 수업 중복 대기 |
| `recurrence (instructor_id, weekday, start_time) WHERE active` | 같은 강사 · 같은 요일 · 같은 시각 규칙 2개 |

- 슬롯 중복 생성은 일반 UNIQUE `(recurrence_id, starts_at)`로 막는다

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| hard delete | 지난 기록 · 재발급 이력 · 휴강 사유가 사라진다 |

## 대가

- 유니크 단위를 나중에 바꾸기 어렵다. 이미 들어간 중복 데이터 때문에 새 유니크 추가가 실패하고, 어느 행을 지울지 사람이 판단해야 한다
- 조회마다 살아 있는 행만 고르는 조건이 붙는다
