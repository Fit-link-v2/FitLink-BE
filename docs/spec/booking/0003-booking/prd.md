# BE-0003 개발 PRD · 예약·취소

> Notion "테이블 설계 · ERD" 문서에서 이 기능에 해당하는 부분을 옮긴 뒤, 2026-09-25 회의 결정([DEC-0001~0006](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/decisions))을 반영했다. 아직 recatch-tdd prd 단계 전이다. [템플릿](../../../templates/prd.md) 형식(인수 조건 · API 절)으로 다시 쓰는 것은 이 기능의 prd 단계에서 한다.

## 근거

- 제품 PRD: [PRD-0003](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/prd/0003-booking)
- 쓰기 경로 현재 전체 모습: [write-paths.md](../write-paths.md)
- 관련 결정: [DEC-0006](https://github.com/Fit-link-v2/Fit-link-PRD/blob/main/decisions/0006-booking-rules.md) (같은 날 여러 예약 · 휴강), [DEC-0005](https://github.com/Fit-link-v2/Fit-link-PRD/blob/main/decisions/0005-validation-target.md) (검증 기준), [BE-ADR-0001](../../../decisions/0001-taken-counter.md), [BE-ADR-0002](../../../decisions/0002-history-tables-first.md), [BE-ADR-0010](../../../decisions/0010-restored-in-0003.md)

## 기술 설계

### 테이블 정의 (DDL)

#### 수강권 보정 이력

```sql
-- 강사가 잔여를 직접 고친 이력 (PRD 3 AC 5.2.2).
CREATE TABLE entitlement_adjustment (
  id             bigserial   PRIMARY KEY,
  entitlement_id bigint      NOT NULL REFERENCES entitlement(id),
  before_used    int         NOT NULL,
  after_used     int         NOT NULL,
  reason         text,
  created_at     timestamptz NOT NULL DEFAULT now()
);
```

#### 예약

```sql
CREATE TABLE booking (
  id             bigserial   PRIMARY KEY,
  member_id      bigint      NOT NULL REFERENCES member(id),
  class_slot_id  bigint      NOT NULL REFERENCES class_slot(id),
  entitlement_id bigint      NOT NULL REFERENCES entitlement(id),
  status         text        NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'CANCELED')),
  cancel_reason  text        CHECK (cancel_reason IN ('MEMBER', 'INSTRUCTOR')),
  restored       boolean,
  created_at     timestamptz NOT NULL DEFAULT now(),
  canceled_at    timestamptz,

  -- 취소 관련 세 컬럼은 CANCELED일 때만 값이 있다.
  CONSTRAINT booking_cancel_shape CHECK (
    (status = 'ACTIVE'
       AND canceled_at IS NULL AND restored IS NULL AND cancel_reason IS NULL)
    OR
    (status = 'CANCELED'
       AND canceled_at IS NOT NULL AND restored IS NOT NULL AND cancel_reason IS NOT NULL)
  ),

  -- 휴강 취소는 마감과 무관하게 항상 복구한다.
  CONSTRAINT booking_instructor_cancel_restores CHECK (
    cancel_reason IS DISTINCT FROM 'INSTRUCTOR' OR restored = true
  )
);

-- 같은 수업 중복 예약 차단. 위반이 곧 409 ALREADY_BOOKED다.
CREATE UNIQUE INDEX booking_active_uidx
  ON booking (member_id, class_slot_id) WHERE status = 'ACTIVE';

CREATE INDEX booking_class_slot_idx
  ON booking (class_slot_id) WHERE status = 'ACTIVE';        -- 강사 명단
CREATE INDEX booking_history_idx
  ON booking (member_id, created_at DESC);              -- 지난 기록 30건
```

`cancel_reason`이 "회원이 스스로 취소"와 "강사가 휴강시켜서 취소"를 가른다. 지난 기록에 사유가 다르게 표시돼야 하기 때문이다(PRD 3 AC 7.3.2, PRD 5 AC 4.3.1). 회원이 취소한 적 없는데 "취소"로만 보이면 강사에게 문의가 가고, 없애려던 대화가 돌아온다.

`booking_instructor_cancel_restores`가 휴강 취소에 "복구함" 표시를 강제한다. 실제로 `used_count`를 돌려줬는지까지는 보장하지 않는다. 그것은 휴강 트랜잭션이 같은 트랜잭션 안에서 지킨다.

`booking`에 노쇼 컬럼이 없다. 예약 시점에 이미 차감했으므로 결석은 "아무것도 하지 않음"으로 처리된다 (PRD 5 AC 5.1.1). 출석 입력 화면도 범위 밖이다.

#### 관측

```sql
CREATE TABLE event_log (
  id         bigserial   PRIMARY KEY,
  type       text        NOT NULL,   -- BOOKING_CREATED | BOOKING_CANCELED
  member_id  bigint      REFERENCES member(id),
  class_slot_id bigint     REFERENCES class_slot(id),
  payload    jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX event_log_week_idx ON event_log (type, created_at);
```

성공 기준이 "강사 5명 중 3명이 각자 4주 연속 사용"이고, 판정은 시스템이 세는 강사별 주간 화면 예약 건수로 한다([DEC-0005](https://github.com/Fit-link-v2/Fit-link-PRD/blob/main/decisions/0005-validation-target.md)). 그래서 강사별 · 주 단위 집계가 필요하다. `event_log`에는 `instructor_id`가 없고 `member_id` → `member.instructor_id`로 강사를 찾는다. `booking`의 `created_at` · `canceled_at`만으로도 주별 예약 · 취소 수는 셀 수 있다. 그래도 별도로 남기는 근거는 아래의 비가역성이다.

이력 테이블은 **늦게 만들면 그 이전 기간을 되살릴 수 없다.** 마이그레이션 난이도는 낮은데 비가역성은 높은 유형이다. [BE-ADR-0002](../../../decisions/0002-history-tables-first.md)에서 다시 다룬다.

### 마이그레이션

PRD 단위가 배포 단위는 아니다. 1차 배포에는 PRD 1~3이 함께 나간다.

| 마이그레이션 | 내용 | 시점 |
|---|---|---|
| 002 | booking(cancel_reason · restored 포함), entitlement_adjustment, event_log | PRD 3. 1차 배포에 포함 |

`restored`는 원래 PRD 5(마이그레이션 004)에서 넣을 예정이었다. 휴강이 PRD 3으로 들어오면서 PRD 3 시점에 이미 "강사 휴강은 항상 복구"를 기록해야 하므로 002로 앞당겼다. 이 시점의 회원 취소는 항상 복구이므로 `restored = true`만 쓰인다. 근거는 [BE-ADR-0010](../../../decisions/0010-restored-in-0003.md).

## AC 대 제약

인수 조건이 어느 제약으로 내려왔는지의 대응표다. 리뷰는 이 표를 기준으로 하면 된다.

| PRD · AC | 요구 | 물리 제약 |
|---|---|---|
| 3 · AC 1.3.1 | 정원 초과 예약 불가 | 조건부 UPDATE + CHECK taken ≤ capacity |
| 3 · AC 1.3.5 | 만료 이른 수강권 자동 선택 | 인덱스 (member_id, window_end) |
| 3 · AC 3.1 | ALREADY_BOOKED | `booking` 부분 유니크 (member_id, class_slot_id) WHERE ACTIVE |
| 3 · AC 7.3.2 · 5 · AC 4.3.1 | 지난 기록에 사유 표시 | `booking.cancel_reason` + `restored` |
| 3 · AC 4.2.3 | 지난 기록 최근 30건 | 인덱스 (member_id, created_at DESC) |
| 3 · AC 5.2.2 | 수정 시각과 이전 값 기록 | `entitlement_adjustment` 테이블 |
| 3 · AC 1.5.1 | 같은 날 여러 예약 허용 | 날짜 단위 유니크 없음 (의도적 부재). 유니크는 (member_id, class_slot_id)뿐 |
| 3 · AC 7.2.1 | 휴강 취소 사유 기록 | `booking.cancel_reason = 'INSTRUCTOR'` |
| 3 · AC 7.2.2 | 휴강은 마감과 무관하게 복구 | `booking_instructor_cancel_restores` CHECK |
| 3 · AC 7.2.4 | 이미 휴강한 슬롯은 변화 없음 | 휴강 UPDATE 조건 `canceled_at IS NULL`. 0행이면 롤백 |
| 3 · AC 7.2.5 | 휴강은 한 트랜잭션 | [쓰기 경로 5.2](../write-paths.md) |

## 열린 질문

| # | 질문 | 영향 |
|---|---|---|
| Q15 | 잔여를 직접 고친 수강권으로 잡힌 예약을 휴강 · 취소하면 | `used_count - 1`이 0 아래로 내려가 CHECK에 걸리고 트랜잭션 전체가 롤백된다. 수기 수정 하한을 ACTIVE 예약 수로 둘지, 복구를 0에서 멈출지 ([쓰기 경로 5.2](../write-paths.md)) |
| Q16 | 휴강을 `event_log`에 남길 것인가 | PRD 3 AC 6.1.1은 "화면에서 일어난 예약 · 취소"다. 휴강 취소를 BOOKING_CANCELED로 셀지, 다른 이벤트로 셀지, 안 셀지 |
| ~~Q6~~ | ~~`event_log`가 MVP에 필요한가~~ | **해소.** 유지한다. 이력은 늦게 만들면 되살릴 수 없다([BE-ADR-0002](../../../decisions/0002-history-tables-first.md)) |
