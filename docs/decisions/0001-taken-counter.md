---
id: BE-ADR-0001
status: accepted
date: 2026-10-01
deciders: [BE]
source: [PRD-0003 AC 1.3.1, PRD-0000 "범위"]
---

# 정원 판정은 `class_slot.taken` 카운터 컬럼으로 한다

## 맥락

마지막 자리를 두 회원이 동시에 누르면 한 명만 예약돼야 한다(PRD 0, PRD 3 AC 1.3.1). 판정 방법은 두 가지다. 슬롯에 현재 인원을 따로 세어 두거나, 매번 `booking`을 센다.

## 결정

`class_slot.taken` 컬럼을 두고 조건부 UPDATE 한 문장으로 판정한다.

```sql
UPDATE class_slot SET taken = taken + 1
 WHERE id = $1 AND taken < capacity AND starts_at > now();
-- 0행이면 SEAT_TAKEN 또는 CLASS_STARTED
```

PostgreSQL은 같은 행을 다른 트랜잭션이 고치는 중이면 두 번째 UPDATE를 기다리게 한 뒤, 갱신된 행에 WHERE 조건을 다시 평가한다. 그래서 낡은 값으로 판단하고 쓰는 일이 생기지 않는다.

`class_slot_taken_range` CHECK(`taken`이 0 이상 `capacity` 이하)를 마지막 방어선으로 둔다.

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| `SELECT ... FOR UPDATE`로 슬롯 행을 잠그고 `booking`을 센다 | 값이 한 곳에만 있어 어긋날 일이 없다. 회원 15~20명 규모면 성능도 문제없다. 버린 이유는 잠금과 계산이 두 문장으로 나뉘어 구현이 길어지기 때문이다. 카운터가 유일한 답은 아니다 |

## 대가

- 같은 사실이 두 곳에 있다. `taken`은 그 슬롯의 ACTIVE 예약 수와 같아야 한다
- CHECK는 상한만 막고 두 값이 같다는 것은 보장하지 않는다. 그래서 예약 · 취소 · 휴강 세 경로가 **모두 한 트랜잭션 안에서** 두 값을 같이 바꿔야 한다
- 어긋나면 대조 · 보정 작업이 필요하다
