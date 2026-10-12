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
| ~~2 · AC 2.1.2~~ | ~~월 정액이면 이번 주기 N회 중 M회~~ | 폐기. 아래 AC 2.1.5 ~ 2.1.9로 대체 |
| 2 · AC 2.1.5 · 2.1.8 | 수강권마다 카드 하나, 합산하지 않음 | 횟수권은 `entitlement` 1행이 카드 하나. 응답 모양은 [BE-0001 API 5절](../../instructor/0001-instructor-setup/prd.md)의 `EntitlementCard`와 같게 하는 것을 제안한다 |
| 2 · AC 2.1.6 | 월 정액은 등록 하나가 카드 하나. 윗줄은 등록 전체, 셋째 줄은 지금 주기 | `subscription` 1행이 카드 하나. 지금 주기는 `entitlement.source_id = subscription.id`이면서 오늘을 덮는 행. 같은 종류 월 정액이 둘이면 카드도 둘 |
| 2 · AC 2.1.7 | 시작 전 수강권도 카드로 | 시작일 > 오늘인 행을 뺀 조회를 하지 않는다. `state = NOT_STARTED` |
| 2 · AC 2.1.8 | 맨 앞은 다음에 차감될 수강권 | 정렬 기준은 PRD-0003 AC 1.3.5와 같다(BE-0001 API 5절 카드 순서) |
| 2 · AC 2.1.9 | 잔여 0회도 종료일까지 표시, 지나면 숨김 | 조회 조건은 `종료일 >= 오늘`. 월 정액은 `subscription.ends_on`으로 판단 |
| 2 · 열린 질문 (이번 주기 소진 화면) | **B안**: 수업 목록 제목 아래에 고른 날짜의 주기와 잔여 | 날짜마다 그 날짜를 덮는 수강권(차감될 것)의 주기 날짜 범위와 잔여를 내려준다. 수업이 있는 날짜는 서버가 수업마다 하는 판정(PRD-0003 AC 1.1.5)과 같은 조회라 추가 비용이 작다. 수업이 없는 날짜는 따로 조회해야 한다. 주기별 수강권은 오픈 범위만큼 미리 있다(PRD-0001 AC 3.2.8). 그 날짜를 횟수권이 덮을 때 무엇을 보일지는 정하지 않았다. FE 승인 전 |

위 행들은 [DEC-0010](https://github.com/Fit-link-v2/Fit-link-PRD/pull/4) (proposed, BE 답 2026-10-12)의 결론이다. FE가 승인하면 PRD에 반영된다. 응답의 필드 이름과 구조는 API 논의에서 정한다(DEC-0010 "home 응답 모양").
