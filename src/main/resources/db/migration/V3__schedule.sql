-- 반복 규칙과 슬롯

-- 매주 같은 요일 · 시각에 여는 수업. 회차는 class_slot에 하나씩 만들어 둔다.
CREATE TABLE recurrence (
  id            bigserial   PRIMARY KEY,
  instructor_id bigint      NOT NULL REFERENCES instructor(id),
  weekday       smallint    NOT NULL CHECK (weekday BETWEEN 0 AND 6),  -- 0=일
  start_time    time        NOT NULL,
  capacity      int         NOT NULL CHECK (capacity BETWEEN 1 AND 50),
  active        boolean     NOT NULL DEFAULT true,
  created_at    timestamptz NOT NULL DEFAULT now(),

  -- class_slot의 복합 FK 대상. id가 이미 유일하므로 행을 더 제한하지 않는다
  CONSTRAINT recurrence_owner_uk UNIQUE (id, instructor_id)
);

CREATE INDEX recurrence_owner_idx ON recurrence (instructor_id);

-- 같은 강사가 같은 요일 · 같은 시각에 켜진 규칙을 두 개 두지 못한다.
CREATE UNIQUE INDEX recurrence_slot_uidx
  ON recurrence (instructor_id, weekday, start_time) WHERE active;

-- 회원이 예약하는 수업 한 번. 규칙이 만든 회차와 일회성 수업이 같은 테이블이다.
CREATE TABLE class_slot (
  id            bigserial   PRIMARY KEY,
  instructor_id bigint      NOT NULL REFERENCES instructor(id),
  recurrence_id bigint,                 -- NULL이면 일회성 수업
  occurs_on     date        NOT NULL,   -- 원래 날짜. 시각을 바꿔도 그대로. 일회성이면 수업 날짜
  starts_at     timestamptz NOT NULL,
  capacity      int         NOT NULL CHECK (capacity >= 0),  -- 생성 시점의 규칙 정원을 복사한다
  taken         int         NOT NULL DEFAULT 0,
  canceled_at   timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now(),

  -- 규칙이 만든 슬롯은 그 규칙의 강사와 같은 instructor_id를 가진다
  CONSTRAINT class_slot_recurrence_owner_fk
    FOREIGN KEY (recurrence_id, instructor_id) REFERENCES recurrence (id, instructor_id),
  -- 시각을 바꾸거나 휴강해도 같은 회차를 다시 만들지 않는다
  CONSTRAINT class_slot_occurrence_uk UNIQUE (recurrence_id, occurs_on),
  CONSTRAINT class_slot_taken_range CHECK (taken >= 0 AND taken <= capacity)
);

-- 같은 강사의 휴강 아닌 수업은 한 시각에 하나다.
-- 회원 화면이 "그 강사의 휴강 아닌 슬롯을 시각순으로" 읽는 인덱스도 겸한다.
CREATE UNIQUE INDEX class_slot_time_uidx
  ON class_slot (instructor_id, starts_at) WHERE canceled_at IS NULL;

-- 회차 생성이 휴강한 행까지 포함해 그 시각에 슬롯이 있는지 본다. 강사 슬롯 목록도 휴강을 포함해 읽는다.
CREATE INDEX class_slot_owner_idx ON class_slot (instructor_id, starts_at);
