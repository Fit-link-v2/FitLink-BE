# 목록

> 지금은 손으로 관리한다. 문서가 늘면 `feature.yaml` · `domain.yaml`에서 자동 생성한다.

## 도메인

| 도메인 | 테이블 | 문서 |
|---|---|---|
| instructor | instructor · setting · instructor_session | [POLICY](spec/instructor/POLICY.md) · [데이터 모델](spec/instructor/data-model.md) |
| schedule | recurrence · class | [POLICY](spec/schedule/POLICY.md) · [데이터 모델](spec/schedule/data-model.md) |
| membership | member · member_link | [POLICY](spec/membership/POLICY.md) · [데이터 모델](spec/membership/data-model.md) |
| entitlement | entitlement · entitlement_adjustment · subscription | [POLICY](spec/entitlement/POLICY.md) · [데이터 모델](spec/entitlement/data-model.md) |
| booking | booking · waitlist | [POLICY](spec/booking/POLICY.md) · [데이터 모델](spec/booking/data-model.md) · [쓰기 경로](spec/booking/write-paths.md) |
| analytics | event_log | [POLICY](spec/analytics/POLICY.md) · [데이터 모델](spec/analytics/data-model.md) |

## 기능

| 번호 | 제목 | 담당 도메인 | 참여 도메인 | 상태 |
|---|---|---|---|---|
| [0001](spec/instructor/0001-instructor-setup/prd.md) | 강사 세팅 | instructor | schedule · membership · entitlement | planned |
| [0002](spec/membership/0002-member-view/prd.md) | 회원 조회 | membership | schedule · entitlement | planned |
| [0003](spec/booking/0003-booking/prd.md) | 예약·취소 | booking | entitlement · schedule · analytics | planned |
| [0004](spec/booking/0004-waitlist/prd.md) | 대기·승계 | booking | schedule | planned |
| [0005](spec/booking/0005-cancel-deadline/prd.md) | 취소 마감 규칙 | booking | instructor · entitlement | planned |

## 아키텍처

- [개요](architecture/overview.md)
- [컨텍스트 맵](architecture/context-map.md) — 지금은 전체 ERD를 임시로 담고 있음
- [설계 판단과 그 대가](architecture/design-tradeoffs.md) — 2차에서 `decisions/`로 나눔

## 결정

없음
