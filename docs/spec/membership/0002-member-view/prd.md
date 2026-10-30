# BE-0002 개발 PRD · 회원 조회

> 아직 recatch-tdd prd 단계 전이다. [템플릿](../../../templates/prd.md) 형식으로 다시 쓰는 것은 이 기능의 prd 단계에서 한다.

## 근거

- 제품 PRD: [PRD-0002](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/prd/0002-member-view)

## AC 대 제약

인수 조건이 어느 제약으로 내려왔는지의 대응표다. 리뷰는 이 표를 기준으로 하면 된다.

| PRD · AC | 요구 | 물리 제약 |
|---|---|---|
| 2 · AC 1.1.3 | 최초 1회 기록 · 매 요청 갱신 | `first_opened_at`과 `last_used_at`을 분리 |
| 2 · AC 1.1.5 · 1.1.6 | 페이지가 열릴 때마다 home API에서 검증, 무효면 에러 코드 | `member_link.token_hash` 전역 UNIQUE + `revoked_at IS NULL` 조건 조회 |
| 2 · AC 3.2.5 | 휴강 슬롯은 표시하지 않음 | `class_slot.canceled_at` soft delete + 부분 인덱스 |
| ~~2 · AC 2.1.2~~ | ~~월 정액이면 이번 주기 N회 중 M회~~ | 폐기. AC 2.1.5 ~ 2.1.9로 대체 |
| 2 · AC 2.1.5 · 2.1.8 | 수강권마다 카드, 합산하지 않음 | 응답은 BE-0001 API 5절의 `EntitlementCard` 목록 |
| 2 · AC 2.1.6 | 월 정액은 등록 하나가 카드 하나 | `subscription` 1행 + `source_id`가 그 등록이고 오늘을 덮는 `entitlement` |
| 2 · AC 2.1.7 · 2.1.9 | 시작 전도 표시, 잔여 0이어도 종료일까지 | 조회 조건은 `종료일 >= 오늘`. 월 정액은 `subscription.ends_on` |
| 2 · 열린 질문 (이번 주기 소진) | 고른 날짜의 주기와 잔여를 표시(B안) | 날짜마다 그 날짜를 덮는 수강권의 주기 · 잔여(주기별 수강권은 오픈 범위만큼 미리 있다, PRD-0001 AC 3.2.8). 수업 없는 날짜는 따로 조회 |

DEC-0010(proposed)의 결론이다. 응답 필드는 API 설계에서 정한다.

## 기술 설계 메모

- 회원 API는 토큰을 경로(`/m/{token}/...`)로 받는다([DEC-0008](https://github.com/Fit-link-v2/Fit-link-PRD/blob/main/decisions/0008-react-spa-backend-cookie.md)). 토큰 → 회원 변환은 한 곳(argument resolver 등)에 모으고, 컨트롤러는 토큰 원문 대신 검증된 회원을 받는다. 나중에 토큰을 헤더로 옮겨도 그 한 곳만 고친다
- 회원 API는 세션 쿠키를 보지 않는다. 강사가 같은 브라우저로 링크를 열면 쿠키가 함께 오기 때문이다
- 토큰 마스킹(PRD-0002 AC 4.2.2)은 Caddy access log와 에러 리포팅에 한다. Spring access log는 켜지 않는다(BE-ADR-0015). 실제 회원에게 첫 링크를 보내기 전에 켜고, 로그를 직접 열어 확인한다. 로그 수집처를 더하거나 바꿀 때마다 다시 확인한다

## 열린 질문

| # | 질문 | 영향 |
|---|---|---|
| Q35 | 이번 주기 소진 화면(B안)에서 고른 날짜를 횟수권이 덮을 때 무엇을 보이나 | 날짜별 응답에 횟수권 잔여를 줄지 |
