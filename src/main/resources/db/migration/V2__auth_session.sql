-- 로그인 세션. auth 모듈이 쓴다. 지금은 강사만 로그인한다

-- 세션 ID 원본은 쿠키에만 있고 DB에는 SHA-256 해시만 남는다. DB가 유출돼도 세션을 가로챌 수 없다.
CREATE TABLE auth_session (
  id            bigserial   PRIMARY KEY,
  instructor_id bigint      NOT NULL REFERENCES instructor(id),
  session_hash  bytea       NOT NULL UNIQUE,   -- SHA-256, 32바이트
  created_at    timestamptz NOT NULL DEFAULT now(),
  last_seen_at  timestamptz NOT NULL DEFAULT now(),
  expires_at    timestamptz NOT NULL,          -- last_seen_at + 14일
  revoked_at    timestamptz                    -- 로그아웃
);

CREATE INDEX auth_session_owner_idx ON auth_session (instructor_id);
