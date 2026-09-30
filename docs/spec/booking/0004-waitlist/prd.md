# BE-0004 개발 PRD · 대기·승계

> **1차 이전본.** Notion "테이블 설계 · ERD" 문서에서 이 기능에 해당하는 부분을 잘라 옮겼다. 내용은 원문 그대로다. [템플릿](../../../templates/prd.md) 형식으로 다시 쓰고 회의 결정을 반영하는 건 2차에서 한다.

## 근거

- 제품 PRD: [PRD-0004](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/prd/0004-waitlist)
- 쓰기 경로 현재 전체 모습: [write-paths.md](../write-paths.md)

## 기술 설계

### 테이블 정의 (DDL)

#### 대기

```sql
CREATE TABLE waitlist (
  id                   bigserial   PRIMARY KEY,
  member_id            bigint      NOT NULL REFERENCES member(id),
  class_id             bigint      NOT NULL REFERENCES class(id),
  status               text        NOT NULL DEFAULT 'WAITING'
                         CHECK (status IN ('WAITING', 'CANCELED', 'CONVERTED')),
  converted_booking_id bigint      REFERENCES booking(id),
  created_at           timestamptz NOT NULL DEFAULT now(),
  canceled_at          timestamptz,

  CONSTRAINT waitlist_converted_shape
    CHECK ((status = 'CONVERTED') = (converted_booking_id IS NOT NULL))
);

CREATE UNIQUE INDEX waitlist_waiting_uidx
  ON waitlist (member_id, class_id) WHERE status = 'WAITING';

-- 순번은 이 인덱스 순서로 ROW_NUMBER()를 매겨 계산한다.
CREATE INDEX waitlist_order_idx
  ON waitlist (class_id, created_at) WHERE status = 'WAITING';
```

`waitlist`에 순번 컬럼이 없다. 가운데 대기자가 빠질 때마다 뒷사람 번호를 다시 쓰는 UPDATE가 필요해지고, 그 사이에 등록이 들어오면 번호가 겹친다. 조회 시점에 계산하면 이 경쟁 자체가 생기지 않는다.

### 마이그레이션

PRD 단위가 배포 단위는 아니다. 1차 배포에는 PRD 1~3이 함께 나간다.

| 마이그레이션 | 내용 | 시점 |
|---|---|---|
| 003 | waitlist | PRD 4 |

## AC 대 제약

인수 조건이 어느 제약으로 내려왔는지의 대응표다. 리뷰는 이 표를 기준으로 하면 된다.

| PRD · AC | 요구 | 물리 제약 |
|---|---|---|
| 4 · AC 1.1.3 | 대기는 차감하지 않음 | `waitlist`에 entitlement_id 없음 |
| 4 · AC 1.1.4 | 대기 중복 등록 차단 | 부분 유니크 (member_id, class_id) WHERE WAITING |
| 4 · AC 2.1.2 | 순번은 created_at 오름차순 | 순번 컬럼 없음. 인덱스 (class_id, created_at) |
| 4 · AC 2.2.2 | 취소 시 뒤 순번 자동 감소 | 계산값이므로 저장 갱신 없음 |
| 4 · AC 3.2.2 | 승계 기록 | `waitlist.converted_booking_id` |
