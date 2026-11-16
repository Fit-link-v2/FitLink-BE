-- 회원과 회원 개인 링크

-- 강사의 회원. 이름은 앞뒤 공백을 애플리케이션이 지운 뒤 저장한다.
-- name에 UNIQUE를 걸지 않는다. 동명이인을 허용하고 메모로 구분한다
CREATE TABLE member (
  id            bigserial   PRIMARY KEY,
  instructor_id bigint      NOT NULL REFERENCES instructor(id),
  name          text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 50),
  memo          text        CHECK (char_length(memo) <= 500),
  status        text        NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'ENDED')),
  created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX member_owner_idx ON member (instructor_id, status);

-- 회원 개인 링크 /m/{token}. 토큰 원본은 저장하지 않고 SHA-256만 둔다.
-- 재발급은 기존 행에 revoked_at을 채우고 새 행을 넣는다
CREATE TABLE member_link (
  id              bigserial   PRIMARY KEY,
  member_id       bigint      NOT NULL REFERENCES member(id),
  token_hash      bytea       NOT NULL UNIQUE
                    CONSTRAINT member_link_token_hash_length CHECK (octet_length(token_hash) = 32),
  issued_at       timestamptz NOT NULL DEFAULT now(),
  first_opened_at timestamptz,
  last_used_at    timestamptz,
  revoked_at      timestamptz
);

-- 회원 1명에게 끊지 않은 링크는 최대 1개
CREATE UNIQUE INDEX member_link_active_uidx
  ON member_link (member_id) WHERE revoked_at IS NULL;
