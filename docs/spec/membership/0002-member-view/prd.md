# BE-0002 개발 PRD · 회원 조회

> Notion "테이블 설계 · ERD" 문서에서 이 기능에 해당하는 부분을 옮긴 뒤, 2026-09-25 회의 결정([DEC-0001~0006](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/decisions))을 반영했다. 아직 recatch-tdd prd 단계 전이다. [템플릿](../../../templates/prd.md) 형식(인수 조건 · API 절)으로 다시 쓰는 것은 이 기능의 prd 단계에서 한다.

## 근거

- 제품 PRD: [PRD-0002](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/prd/0002-member-view)

## AC 대 제약

인수 조건이 어느 제약으로 내려왔는지의 대응표다. 리뷰는 이 표를 기준으로 하면 된다.

| PRD · AC | 요구 | 물리 제약 |
|---|---|---|
| 2 · AC 1.1.3 | 최초 1회 기록 · 매 요청 갱신 | `first_opened_at`과 `last_used_at`을 분리 |
| 2 · AC 3.2.5 | 휴강 슬롯은 표시하지 않음 | `class_slot.canceled_at` soft delete + 부분 인덱스 |
| 2 · AC 2.1.2 | 월 정액이면 이번 주기 N회 중 M회 | 오늘을 덮는 `kind = 'SUBSCRIPTION'` 행 1개의 `max_count`와 `used_count` |
