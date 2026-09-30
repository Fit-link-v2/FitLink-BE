# BE-0003 개발 PRD · 예약·취소

> **1차 이전본.** Notion "테이블 설계 · ERD" 문서에서 이 기능에 해당하는 부분을 잘라 옮겼다. 내용은 원문 그대로다. [템플릿](../../../templates/prd.md) 형식으로 다시 쓰고 회의 결정을 반영하는 건 2차에서 한다.

## 근거

- 제품 PRD: [PRD-0003](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/prd/0003-booking)
- 쓰기 경로 현재 전체 모습: [write-paths.md](../write-paths.md)

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
  class_id       bigint      NOT NULL REFERENCES class(id),
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
  ON booking (member_id, class_id) WHERE status = 'ACTIVE';

CREATE INDEX booking_class_idx
  ON booking (class_id) WHERE status = 'ACTIVE';        -- 강사 명단
CREATE INDEX booking_history_idx
  ON booking (member_id, created_at DESC);              -- 지난 기록 30건
```

`cancel_reason`이 "회원이 스스로 취소"와 "강사가 휴강시켜서 취소"를 가른다. PRD 3 AC 4.2의 지난 기록에 사유가 다르게 표시돼야 하기 때문이다. 회원이 취소한 적 없는데 "취소"로만 보이면 강사에게 문의가 가고, 없애려던 대화가 돌아온다.

`booking_instructor_cancel_restores`가 휴강 정책을 DB에 못 박는다. 강사 사정으로 수업이 없어졌는데 차감이 남는 상태를 DB가 거부한다.

`booking`에 노쇼 컬럼이 없다. 예약 시점에 이미 차감했으므로 결석은 "아무것도 하지 않음"으로 처리된다 (PRD 5 AC 5.1.1). 출석 입력 화면도 범위 밖이다.

#### 관측

```sql
CREATE TABLE event_log (
  id         bigserial   PRIMARY KEY,
  type       text        NOT NULL,   -- BOOKING_CREATED | BOOKING_CANCELED
  member_id  bigint      REFERENCES member(id),
  class_id   bigint      REFERENCES class(id),
  payload    jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX event_log_week_idx ON event_log (type, created_at);
```

성공 기준이 "강사 1명이 4주 연속 카톡 대신 사용"이므로 주 단위 집계가 필요하다. `booking` 테이블만으로도 대부분 셀 수 있지만, 취소된 예약은 행이 갱신되어 원래 시각이 지워지지 않게 별도로 남긴다.

이력 테이블은 **늦게 만들면 그 이전 기간을 되살릴 수 없다.** 마이그레이션 난이도는 낮은데 비가역성은 높은 유형이다. [`design-tradeoffs.md`](../../../architecture/design-tradeoffs.md)에서 다시 다룬다.

### 마이그레이션

PRD 단위가 배포 단위는 아니다. 1차 배포에는 PRD 1~3이 함께 나간다.

| 마이그레이션 | 내용 | 시점 |
|---|---|---|
| 002 | booking(cancel_reason 포함, restored 제외), entitlement_adjustment, event_log | PRD 3. 1차 배포에 포함 |

## AC 대 제약

인수 조건이 어느 제약으로 내려왔는지의 대응표다. 리뷰는 이 표를 기준으로 하면 된다.

| PRD · AC | 요구 | 물리 제약 |
|---|---|---|
| 3 · AC 1.3.1 | 정원 초과 예약 불가 | 조건부 UPDATE + CHECK taken ≤ capacity |
| 3 · AC 1.3.5 | 만료 이른 수강권 자동 선택 | 인덱스 (member_id, window_end) |
| 3 · AC 3.1 | ALREADY_BOOKED | `booking` 부분 유니크 (member_id, class_id) WHERE ACTIVE |
| 3 · AC 4.2 | 지난 기록에 사유 표시 | `booking.cancel_reason` + `restored` |
| 3 · AC 4.2.3 | 지난 기록 최근 30건 | 인덱스 (member_id, created_at DESC) |
| 3 · AC 5.2.2 | 수정 시각과 이전 값 기록 | `entitlement_adjustment` 테이블 |

## 열린 질문

| # | 질문 | 영향 |
|---|---|---|
| Q6 | `event_log`가 MVP에 필요한가 | `booking`의 created_at · canceled_at으로도 주 단위 집계가 대부분 가능하다. 다만 이력은 늦게 만들면 되살릴 수 없으므로 빼는 결정은 신중해야 한다 |
