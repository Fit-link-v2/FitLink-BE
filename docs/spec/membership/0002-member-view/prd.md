# BE-0002 개발 PRD · 회원 조회

> **1차 이전본.** Notion "테이블 설계 · ERD" 문서에서 이 기능에 해당하는 부분을 잘라 옮겼다. 내용은 원문 그대로다. [템플릿](../../../templates/prd.md) 형식으로 다시 쓰고 회의 결정을 반영하는 건 2차에서 한다.

## 근거

- 제품 PRD: [PRD-0002](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/prd/0002-member-view)

## AC 대 제약

인수 조건이 어느 제약으로 내려왔는지의 대응표다. 리뷰는 이 표를 기준으로 하면 된다.

| PRD · AC | 요구 | 물리 제약 |
|---|---|---|
| 2 · AC 1.1.3 | 최초 1회 기록 · 매 요청 갱신 | `first_opened_at`과 `last_used_at`을 분리 |
| 2 · AC 3.2.5 | 휴강 슬롯은 표시하지 않음 | `class.canceled_at` soft delete + 부분 인덱스 |
