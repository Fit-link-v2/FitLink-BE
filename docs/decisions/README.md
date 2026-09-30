# 설계 결정 (ADR)

BE 내부 설계 결정. FE와 같이 따르는 결정은 [PRD 레포 `decisions/`](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/decisions)에 둔다.

- 파일 이름: `{4자리}-{영문-슬러그}.md`. ID는 `BE-ADR-{4자리}`
- 형식: [`templates/decision.md`](../templates/decision.md) (MADR)
- 한 번 `accepted`가 된 결정은 고치지 않는다. 바뀌면 새 번호를 만들고 이전 결정의 상태를 `superseded by BE-ADR-XXXX`로 바꾼다

| ID | 제목 | 상태 |
|---|---|---|
| [BE-ADR-0001](0001-taken-counter.md) | 정원 판정은 `class_slot.taken` 카운터 컬럼으로 한다 | accepted |
| [BE-ADR-0002](0002-history-tables-first.md) | 이력 테이블은 처음부터 만든다 | accepted |
| [BE-ADR-0003](0003-soft-delete-and-uniqueness.md) | 지우지 않고 상태로 남기고, 유니크는 부분 인덱스로 건다 | accepted |
| [BE-ADR-0004](0004-status-as-text.md) | 상태 값은 `text` + CHECK로 표현한다 | accepted |
| [BE-ADR-0005](0005-snapshot-capacity.md) | 슬롯의 정원은 생성 시점에 복사한다 | accepted |
| [BE-ADR-0006](0006-time-and-timezone.md) | 시각은 `timestamptz`, 날짜 계산의 타임존은 `Asia/Seoul` 상수로 한다 | accepted |
| [BE-ADR-0007](0007-class-slot-naming.md) | 슬롯 테이블 이름은 `class_slot`이다 | accepted |
| [BE-ADR-0008](0008-session-store.md) | 강사 세션 저장소를 직접 만들지, Spring Session JDBC를 쓸지 | proposed |
| [BE-ADR-0009](0009-entitlement-kind-exclusion.md) | 수강권 종류 겹침은 등록 검사 + EXCLUDE 제약 두 겹으로 막는다 | accepted |
| [BE-ADR-0010](0010-restored-in-0003.md) | `booking.restored`는 기능 0003(마이그레이션 002)에서 만든다 | accepted |
