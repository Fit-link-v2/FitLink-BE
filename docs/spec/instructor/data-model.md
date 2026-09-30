# 강사 도메인 데이터 모델

도메인 사이 관계는 [컨텍스트 맵](../../architecture/context-map.md). 다른 도메인 테이블은 이름과 id만 그렸다.

## ERD

```mermaid
erDiagram
    INSTRUCTOR ||--|| SETTING : "자기 설정"
    INSTRUCTOR ||--o{ INSTRUCTOR_SESSION : "로그인 세션"
    INSTRUCTOR {
        bigint id PK
        text provider "KAKAO"
        text provider_user_id UK
        text email
        text name
    }
    SETTING {
        bigint instructor_id PK
        int open_range_days "기본 14"
        int cancel_deadline_hours "기본 3"
    }
    INSTRUCTOR_SESSION {
        bigint id PK
        bigint instructor_id FK
        bytea session_hash UK "sha256 32B"
        timestamptz created_at
        timestamptz last_seen_at
        timestamptz expires_at "sliding 14일"
        timestamptz revoked_at "로그아웃"
    }
```

## 테이블

| 테이블 | 도입 기능 |
|---|---|
| `instructor` | 0001 |
| `setting` | 0001 |
| `instructor_session` | 0001 |
