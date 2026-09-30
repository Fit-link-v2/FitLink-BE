# 관측 도메인 데이터 모델

도메인 사이 관계는 [컨텍스트 맵](../../architecture/context-map.md). 다른 도메인 테이블은 이름과 id만 그렸다.

## ERD

```mermaid
erDiagram
    MEMBER ||--o{ EVENT_LOG : "행위 주체"
    CLASS_SLOT ||--o{ EVENT_LOG : "대상"
    EVENT_LOG {
        bigint id PK
        text type "BOOKING_CREATED BOOKING_CANCELED"
        bigint member_id FK
        bigint class_slot_id FK
        jsonb payload
        timestamptz created_at
    }
    MEMBER {
        bigint id PK "다른 도메인"
    }
    CLASS_SLOT {
        bigint id PK "다른 도메인"
    }
```

## 테이블

| 테이블 | 도입 기능 |
|---|---|
| `event_log` | 0003 |
