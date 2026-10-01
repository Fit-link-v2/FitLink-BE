# 시간표 도메인 데이터 모델

도메인 사이 관계는 [컨텍스트 맵](../../architecture/context-map.md). 다른 도메인 테이블은 이름과 id만 그렸다.

## ERD

```mermaid
erDiagram
    INSTRUCTOR ||--o{ RECURRENCE : "자기 시간표"
    RECURRENCE ||--o{ CLASS_SLOT : "슬롯 생성"
    RECURRENCE {
        bigint id PK
        bigint instructor_id FK
        smallint weekday "0=일"
        time start_time
        int capacity "1~50"
        boolean active
    }
    CLASS_SLOT {
        bigint id PK
        bigint recurrence_id FK
        date occurs_on "원래 날짜"
        timestamptz starts_at
        int capacity "생성 시점 복사"
        int taken "조건부 UPDATE 대상"
        timestamptz canceled_at "휴강"
    }
    INSTRUCTOR {
        bigint id PK "다른 도메인"
    }
```

## 테이블

| 테이블 | 도입 기능 |
|---|---|
| `recurrence` | 0001 |
| `class_slot` | 0001 |
