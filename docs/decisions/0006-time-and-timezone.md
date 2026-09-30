---
id: BE-ADR-0006
status: accepted
date: 2026-10-01
deciders: [BE]
source: [BE-0001 열린 질문 Q4, PRD-0005 AC 2.1.1]
---

# 시각은 `timestamptz`, 날짜 계산의 타임존은 `Asia/Seoul` 상수로 한다

## 맥락

수업 시각(`class_slot.starts_at`)은 순간이고, 수강권 기간(`entitlement.window_start/end`)과 월 정액 시작일은 날짜다. 둘을 비교할 때 어느 타임존의 날짜로 볼지 정해야 한다. 강사가 여러 명이 되면서 강사별 타임존 설정이 필요한지도 논의됐다(BE-0001 Q4).

## 결정

- 시각은 모두 `timestamptz`로 저장한다
- 날짜로 바꿀 때는 DB 세션 타임존에 기대지 않고 `AT TIME ZONE 'Asia/Seoul'`을 명시한다
- 타임존은 상수 `Asia/Seoul` 하나다. 강사별 설정 컬럼을 두지 않는다
- 마감 판정 같은 "지금" 비교는 DB의 `now()`로 한다. 클라이언트 시각은 쓰지 않는다

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| `timestamp` (타임존 없음) | 이미 저장된 값이 어느 타임존 기준인지 DB가 모른다 |
| `setting.timezone` 컬럼 | 국내 강사만 받는 동안은 쓰일 일이 없다 |

## 대가

- 해외 강사를 받게 되면 `setting`에 타임존 컬럼을 추가하고, 상수를 쓰는 모든 쿼리를 바꿔야 한다
