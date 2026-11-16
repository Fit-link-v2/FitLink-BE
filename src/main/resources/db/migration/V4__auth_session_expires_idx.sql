-- 매일 자정 만료 세션 삭제(expires_at <= now())가 표 전체를 읽지 않게 한다
CREATE INDEX auth_session_expires_idx ON auth_session (expires_at);
