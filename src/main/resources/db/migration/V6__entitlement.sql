-- 수강권(횟수권 · 월 정액)과 월 정액 등록

-- 월 정액 등록 1건. 차감 대상이 아니라 주기별 entitlement를 만들어내는 설정이다.
-- entitlement.source_id가 이 테이블을 가리키므로 먼저 만든다
CREATE TABLE subscription (
  id               bigserial   PRIMARY KEY,
  member_id        bigint      NOT NULL REFERENCES member(id),
  -- WEEK = 주당 N회(주기 7일), FOUR_WEEKS = 4주마다 N회(주기 28일)
  period_unit      text        NOT NULL CHECK (period_unit IN ('WEEK', 'FOUR_WEEKS')),
  count_per_period int         NOT NULL CHECK (count_per_period BETWEEN 1 AND 99),
  starts_on        date        NOT NULL,   -- 강사가 지정한 시작일. 주기는 여기서부터 센다
  period_count     int         NOT NULL CHECK (period_count > 0),   -- 주기 수. 8주 등록이면 WEEK 8, FOUR_WEEKS 2
  ends_on          date        NOT NULL,   -- 마지막 주기의 마지막 날. 등록할 때 계산해 저장
  created_at       timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT subscription_range_order CHECK (starts_on <= ends_on),
  -- 등록 기간은 4주 단위다. 주당 방식은 주기 수가 4의 배수여야 한다
  CONSTRAINT subscription_four_week_multiple
    CHECK (period_unit <> 'WEEK' OR period_count % 4 = 0),
  -- 종료일은 시작일과 주기 수로 정해진다
  CONSTRAINT subscription_end
    CHECK (ends_on = starts_on
                     + (CASE period_unit WHEN 'WEEK' THEN 7 ELSE 28 END) * period_count - 1),
  -- 등록 기간은 최대 48주
  CONSTRAINT subscription_max_length CHECK (ends_on - starts_on < 336)
);

CREATE INDEX subscription_member_idx ON subscription (member_id);

-- 횟수권과 월 정액의 차이는 컬럼이 아니라 행의 수명이다. 횟수권은 구매 1건에 행 1개이고 window가 유효 기간 전체,
-- 월 정액은 주기마다 행이 새로 생기고 window가 그 주기다. 예약 · 차감 쿼리는 양쪽에서 같다
CREATE TABLE entitlement (
  id           bigserial   PRIMARY KEY,
  member_id    bigint      NOT NULL REFERENCES member(id),
  kind         text        NOT NULL CHECK (kind IN ('PASS', 'SUBSCRIPTION')),
  window_start date        NOT NULL,
  window_end   date        NOT NULL,
  max_count    int         NOT NULL CHECK (max_count BETWEEN 1 AND 999),
  used_count   int         NOT NULL DEFAULT 0,
  source_id    bigint      REFERENCES subscription(id),   -- 월 정액이면 그 등록, 횟수권이면 NULL
  created_at   timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT entitlement_window_order CHECK (window_start <= window_end),
  -- 잔여는 0 이상 max_count 이하. 강사의 수기 수정도 이 범위를 벗어날 수 없다
  CONSTRAINT entitlement_used_range
    CHECK (used_count >= 0 AND used_count <= max_count),
  -- 월 정액 행은 반드시 등록을 가리키고, 횟수권 행은 아무것도 가리키지 않는다
  CONSTRAINT entitlement_source_shape
    CHECK ((kind = 'SUBSCRIPTION') = (source_id IS NOT NULL))
);

-- 예약 시 "유효한 것 중 만료가 가장 이른 1장"을 고른다.
CREATE INDEX entitlement_pick_idx ON entitlement (member_id, window_end);

-- 같은 월 정액의 같은 주기는 한 번만 만든다. 배치를 여러 번 돌려도 결과가 같다.
CREATE UNIQUE INDEX entitlement_period_uidx
  ON entitlement (source_id, window_start) WHERE source_id IS NOT NULL;

-- 한 회원이 다른 종류의 수강권을 기간이 겹치게 가질 수 없다.
-- 같은 종류끼리는 겹쳐도 된다. <> 비교에는 btree_gist 확장이 필요하다.
-- 아직 만들지 않은 월 정액 주기는 보지 못하므로, 등록할 때 애플리케이션이 등록 전체 기간과 먼저 비교한다
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE entitlement ADD CONSTRAINT entitlement_kind_no_overlap
  EXCLUDE USING gist (
    member_id WITH =,
    kind WITH <>,
    (daterange(window_start, window_end, '[]')) WITH &&
  );
