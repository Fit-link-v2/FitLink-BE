# BE-0005 개발 PRD · 취소 마감 규칙

> **1차 이전본.** Notion "테이블 설계 · ERD" 문서에서 이 기능에 해당하는 부분을 잘라 옮겼다. 내용은 원문 그대로다. [템플릿](../../../templates/prd.md) 형식으로 다시 쓰고 회의 결정을 반영하는 건 2차에서 한다.

## 근거

- 제품 PRD: [PRD-0005](https://github.com/Fit-link-v2/Fit-link-PRD/tree/main/prd/0005-cancel-deadline)
- 쓰기 경로 현재 전체 모습: [write-paths.md](../write-paths.md)

## 기술 설계

### 마이그레이션

PRD 단위가 배포 단위는 아니다. 1차 배포에는 PRD 1~3이 함께 나간다.

| 마이그레이션 | 내용 | 시점 |
|---|---|---|
| 004 | setting.cancel_deadline_hours 추가, booking.restored 추가, CHECK 교체 | PRD 5 |

002 시점의 `booking_cancel_shape` CHECK에는 `restored`가 들어가지 않는다. 그 컬럼이 아직 없기 때문이다. 004에서 컬럼을 추가하고 CHECK를 교체한다.

004는 한 덩어리로 실행할 수 없다. **컬럼 추가 → 기존 CANCELED 행 backfill → CHECK 추가** 세 단계로 나눈다. 기존 행에 `restored`가 NULL인 상태에서 CHECK를 걸면 실패한다.

## AC 대 제약

인수 조건이 어느 제약으로 내려왔는지의 대응표다. 리뷰는 이 표를 기준으로 하면 된다.

| PRD · AC | 요구 | 물리 제약 |
|---|---|---|
| 5 · AC 1.1.1 | 마감 0~72시간 | `setting` CHECK cancel_deadline_hours BETWEEN 0 AND 72 |
| 5 · AC 1.1.2 | 설정 1개 (**강사당**으로 수정됨) | `setting` PK가 instructor_id |
| 5 · AC 2.1.1 | 서버 시각으로 판정 | `timestamptz` · DB now(). 클라이언트 값 저장 안 함 |
| 5 · AC 4.1.1 / 4.2.2 | 복구 여부 기록 | `booking.restored` · CANCELED일 때만 NOT NULL인 CHECK |
| 5 · AC 5.1.1 | 노쇼에 아무 동작 없음 | 노쇼 컬럼 · 상태 · 배치 모두 없음 (의도적 부재) |
