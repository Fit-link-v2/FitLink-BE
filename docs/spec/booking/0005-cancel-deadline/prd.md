# BE-0005 개발 PRD · 취소 마감 규칙

> Notion "테이블 설계 · ERD" 문서에서 이 기능에 해당하는 부분을 옮긴 뒤, 2026-09-25 회의 결정([DEC-0001~0006](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/decisions))을 반영했다. 아직 recatch-tdd prd 단계 전이다. [템플릿](../../../templates/prd.md) 형식(인수 조건 · API 절)으로 다시 쓰는 것은 이 기능의 prd 단계에서 한다.

## 근거

- 제품 PRD: [PRD-0005](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/prd/0005-cancel-deadline)
- 쓰기 경로 현재 전체 모습: [write-paths.md](../write-paths.md)

## 기술 설계

### 마이그레이션

PRD 단위가 배포 단위는 아니다. 1차 배포에는 PRD 1~3이 함께 나간다.

| 마이그레이션 | 내용 | 시점 |
|---|---|---|
| 004 | setting.cancel_deadline_hours 추가 | PRD 5 |

```sql
ALTER TABLE setting
  ADD COLUMN cancel_deadline_hours int NOT NULL DEFAULT 3
    CHECK (cancel_deadline_hours BETWEEN 0 AND 72);
```

`booking.restored`는 휴강이 PRD 3으로 옮겨가면서 002(기능 0003)로 앞당겨졌다([BE-ADR-0010](../../../decisions/0010-restored-in-0003.md)). 그래서 004에는 컬럼 하나만 남는다. `cancel_deadline_hours`는 기본값이 있는 NOT NULL 컬럼이라 기존 강사 행에도 한 문장으로 들어간다. 이 기능부터 회원의 마감 후 취소가 `restored = false`를 쓴다.

## AC 대 제약

인수 조건이 어느 제약으로 내려왔는지의 대응표다. 리뷰는 이 표를 기준으로 하면 된다.

| PRD · AC | 요구 | 물리 제약 |
|---|---|---|
| 5 · AC 1.1.1 | 마감 0~72시간 | `setting` CHECK cancel_deadline_hours BETWEEN 0 AND 72 |
| 5 · AC 1.1.2 | 강사마다 설정 1개 | `setting` PK가 instructor_id |
| 5 · AC 2.1.1 | 서버 시각으로 판정 | `timestamptz` · DB now(). 클라이언트 값 저장 안 함 |
| 5 · AC 4.1.1 / 4.2.2 | 복구 여부 기록 | `booking.restored` · CANCELED일 때만 NOT NULL인 CHECK |
| 5 · AC 5.1.1 | 노쇼에 아무 동작 없음 | 노쇼 컬럼 · 상태 · 배치 모두 없음 (의도적 부재) |
