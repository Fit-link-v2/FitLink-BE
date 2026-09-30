# 컨텍스트 맵

> 1차 이전본. 도메인별 ERD로 나누기 전까지 **전체 ERD를 임시로 여기 둔다.** 2차에서 도메인 관계와 도메인 사이 FK만 남긴다.

## 전체 ERD

엔티티 11개. `||--o{`는 1 대 다, `||--||`는 1 대 1, `||--o|`는 1 대 0또는1이다.

```mermaid
erDiagram
    INSTRUCTOR ||--|| SETTING : "자기 설정"
    INSTRUCTOR ||--o{ MEMBER : "자기 회원"
    INSTRUCTOR ||--o{ RECURRENCE : "자기 시간표"
    INSTRUCTOR {
        bigint id PK
        text provider "GOOGLE KAKAO"
        text provider_user_id UK
        text email
        text name
    }
    SETTING {
        bigint instructor_id PK
        int open_range_days "기본 14"
        int cancel_deadline_hours "기본 3"
    }

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
        timestamptz starts_at
        int capacity "생성 시점 복사"
        int taken "조건부 UPDATE 대상"
        timestamptz canceled_at "휴강"
    }

    MEMBER ||--o{ MEMBER_LINK : "링크 발급"
    MEMBER ||--o{ ENTITLEMENT : "수강권 보유"
    MEMBER ||--o{ BOOKING : "예약"
    MEMBER ||--o{ WAITLIST : "대기"
    MEMBER {
        bigint id PK
        bigint instructor_id FK
        text name "동명이인 허용"
        text memo
        text status "ACTIVE ENDED"
    }
    MEMBER_LINK {
        bigint id PK
        bigint member_id FK
        bytea token_hash UK "sha256 32B"
        timestamptz issued_at
        timestamptz first_opened_at
        timestamptz last_used_at
        timestamptz revoked_at
    }

    ENTITLEMENT ||--o{ BOOKING : "차감"
    ENTITLEMENT ||--o{ ENTITLEMENT_ADJUSTMENT : "수기 보정"
    ENTITLEMENT {
        bigint id PK
        bigint member_id FK
        text kind "PASS SUBSCRIPTION"
        date window_start
        date window_end
        int max_count
        int used_count
        bigint source_id FK "월정액 원본"
    }
    ENTITLEMENT_ADJUSTMENT {
        bigint id PK
        bigint entitlement_id FK
        int before_used
        int after_used
        text reason
        timestamptz created_at
    }

    CLASS_SLOT ||--o{ BOOKING : "자리"
    CLASS_SLOT ||--o{ WAITLIST : "대기열"
    BOOKING {
        bigint id PK
        bigint member_id FK
        bigint class_id FK
        bigint entitlement_id FK
        text status "ACTIVE CANCELED"
        text cancel_reason "MEMBER INSTRUCTOR"
        boolean restored "취소 시에만"
        timestamptz created_at
        timestamptz canceled_at
    }

    WAITLIST ||--o| BOOKING : "승계"
    WAITLIST {
        bigint id PK
        bigint member_id FK
        bigint class_id FK
        text status "WAITING CANCELED CONVERTED"
        bigint converted_booking_id FK
        timestamptz created_at
        timestamptz canceled_at
    }

    MEMBER ||--o{ EVENT_LOG : "행위 주체"
    CLASS_SLOT ||--o{ EVENT_LOG : "대상"
    EVENT_LOG {
        bigint id PK
        text type "BOOKING_CREATED BOOKING_CANCELED"
        bigint member_id FK
        bigint class_id FK
        jsonb payload
        timestamptz created_at
    }
```

| 테이블 | 도출 PRD | 역할 |
|---|---|---|
| instructor | 1 | 강사 계정. OAuth 가입. **강사마다 1행** |
| setting | 1 · 5 | **강사별** 설정. 오픈 범위, 취소 마감 시간 |
| recurrence | 1 | 반복 규칙. 요일 · 시각 · 정원. 매주 고정 |
| class | 1 | 슬롯. 회원이 예약하는 단위. 그림에서는 CLASS_SLOT (열린 질문 Q1) |
| member | 1 | 회원. 강사 1명에게 귀속 |
| member_link | 1 · 2 | 개인 링크 토큰의 해시. 재발급하면 행이 늘어난다 |
| entitlement | 0 정의 · 1 생성 | 수강권. 횟수권과 월 정액을 한 모델로 표현 |
| entitlement_adjustment | 3 | 강사의 수기 잔여 보정 이력 |
| booking | 3 · restored는 5 | 예약. 어느 수강권에서 차감했는지와 취소 사유를 보관 |
| waitlist | 4 | 대기. 순번 컬럼은 없다 |
| event_log | 3 | 성공 지표 집계용 이벤트. 화면에 노출하지 않는다 |

## 데이터 격리

소유자 컬럼은 **뿌리 세 곳에만** 둔다. 나머지 테이블은 부모를 타고 올라가면 소유자를 알 수 있다.

| 테이블 | `instructor_id` | 소유자를 어떻게 아나 |
|---|---|---|
| setting | **PK 그 자체** | 직접 |
| member | **있음** | 직접. 부모가 없다 |
| recurrence | **있음** | 직접. 부모가 없다 |
| class | 없음 | `recurrence_id` → `recurrence.instructor_id` |
| member_link · entitlement · booking · waitlist · event_log | 없음 | `member_id` → `member.instructor_id` |
| entitlement_adjustment | 없음 | `entitlement_id` → `entitlement.member_id` → `member.instructor_id` |

중복 컬럼을 두면 부모와 어긋날 수 있다. 테이블 11개에 조인이 얕으므로 부모를 타는 쪽을 택한다.

격리는 DB가 자동으로 해주지 않는다. **강사 화면의 모든 쿼리에 소유자 조건이 들어가야 한다.** 한 군데 빠뜨리면 남의 데이터가 나온다. 강사가 1명뿐인 테스트 데이터에서는 조건을 빠뜨려도 결과가 같아 통과하므로, **테스트 데이터에 강사를 반드시 2명 이상 만든다.**

### 강사 화면의 격리 쿼리

모든 강사 화면 쿼리에 소유자 조건이 들어간다.

```sql
-- 회원 목록
SELECT * FROM member
 WHERE instructor_id = $me AND status = 'ACTIVE';

-- 시간표. class에는 instructor_id가 없으므로 recurrence를 탄다
SELECT c.*
  FROM class c
  JOIN recurrence r ON r.id = c.recurrence_id
 WHERE r.instructor_id = $me
   AND c.canceled_at IS NULL
   AND c.starts_at >= now();

-- 수업별 예약자 명단
SELECT m.name
  FROM booking b
  JOIN member m     ON m.id = b.member_id
  JOIN class c      ON c.id = b.class_id
  JOIN recurrence r ON r.id = c.recurrence_id
 WHERE b.class_id = $1 AND b.status = 'ACTIVE'
   AND r.instructor_id = $me;   -- 남의 수업 id를 넣어도 빈 결과
```

마지막 쿼리의 소유자 조건이 특히 중요하다. 없으면 다른 강사의 `class_id`를 넣는 것만으로 그쪽 명단이 나온다.
