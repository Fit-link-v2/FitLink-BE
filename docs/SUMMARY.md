# 목록

> 지금은 손으로 관리한다. 문서가 늘면 `feature.yaml` · `domain.yaml`에서 자동 생성한다.

## 도메인

| 도메인 | 테이블 | 문서 |
|---|---|---|
| instructor | instructor · setting · instructor_session | [POLICY](spec/instructor/POLICY.md) · [데이터 모델](spec/instructor/data-model.md) |
| schedule | recurrence · class_slot | [POLICY](spec/schedule/POLICY.md) · [데이터 모델](spec/schedule/data-model.md) |
| membership | member · member_link | [POLICY](spec/membership/POLICY.md) · [데이터 모델](spec/membership/data-model.md) |
| entitlement | entitlement · subscription · entitlement_adjustment | [POLICY](spec/entitlement/POLICY.md) · [데이터 모델](spec/entitlement/data-model.md) · [쓰기 경로](spec/entitlement/write-paths.md) |
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
- [컨텍스트 맵](architecture/context-map.md) — 도메인 관계 · 도메인을 넘는 외래 키 · 데이터 격리

## 결정

BE 전용 결정. FE와 같이 따르는 결정은 [PRD 레포 `decisions/`](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/decisions).

- [BE-ADR-0001](decisions/0001-taken-counter.md) 정원 판정은 `class_slot.taken` 카운터 컬럼으로 한다 (accepted)
- [BE-ADR-0002](decisions/0002-history-tables-first.md) 이력 테이블은 처음부터 만든다 (accepted)
- [BE-ADR-0003](decisions/0003-soft-delete-and-uniqueness.md) 지우지 않고 상태로 남기고, 유니크는 부분 인덱스로 건다 (accepted)
- [BE-ADR-0004](decisions/0004-status-as-text.md) 상태 값은 `text` + CHECK로 표현한다 (accepted)
- [BE-ADR-0005](decisions/0005-snapshot-capacity.md) 슬롯의 정원은 생성 시점에 복사한다 (accepted)
- [BE-ADR-0006](decisions/0006-time-and-timezone.md) 시각은 `timestamptz`, 날짜 계산의 타임존은 `Asia/Seoul` 상수로 한다 (accepted)
- [BE-ADR-0007](decisions/0007-class-slot-naming.md) 슬롯 테이블 이름은 `class_slot`이다 (accepted)
- [BE-ADR-0008](decisions/0008-session-store.md) 강사 세션 저장소를 직접 만들지, Spring Session JDBC를 쓸지 (proposed)
- [BE-ADR-0009](decisions/0009-entitlement-kind-exclusion.md) 수강권 종류 겹침은 등록 검사 + EXCLUDE 제약 두 겹으로 막는다 (accepted)
- [BE-ADR-0010](decisions/0010-restored-in-0003.md) `booking.restored`는 기능 0003(마이그레이션 002)에서 만든다 (accepted)
