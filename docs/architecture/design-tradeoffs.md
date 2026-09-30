> 1차 이전본. 2차에서 [`decisions/`](../decisions/) 번호 파일로 나눈다.

# 설계 판단과 그 대가

## `class.taken`은 중복된 진실이다

`taken`은 `booking`에서 `status = 'ACTIVE'`인 행을 센 값과 같아야 한다. 같은 사실이 두 군데 저장돼 있다는 뜻이다.

| 얻는 것 | 치르는 것 |
|---|---|
| `class` 행 하나만 잠그면 정원 판정이 끝난다 | 두 값이 어긋날 수 있다 |
| 잠금을 따로 걸지 않고 한 문장으로 막는다 | 어긋나면 대조 · 보정 작업이 필요하다 |

`class_taken_range` CHECK는 상한만 막는다. `taken`과 실제 예약 수가 같다는 것을 보장하지는 않는다. 그래서 예약 · 취소 · 휴강 세 경로가 **모두 한 트랜잭션 안에서** 두 값을 같이 바꿔야 한다.

**대안도 있다.** `SELECT ... FOR UPDATE`로 `class` 행을 먼저 잠그고 `booking`을 세는 방식이다. 값이 한 군데에만 있어 어긋날 일이 없다. 회원 15~20명 규모면 성능도 문제되지 않는다. 카운터 컬럼이 유일한 답은 아니며, "잠금을 따로 걸지 않고 한 문장으로 끝내려면" 필요한 것이다.

## 되돌리기 어려운 결정

나중에 바꾸는 비용이 높은 순서다. 나머지는 테이블 추가로 해결된다.

| 결정 | 이 설계의 선택 | 나중에 바꾸면 |
|---|---|---|
| 유니크 제약의 단위 | `(recurrence_id, starts_at)`, `(instructor_id, weekday, start_time)` | 이미 들어간 중복 데이터 때문에 추가가 실패한다. 어느 행을 지울지 사람이 판단해야 해 자동화가 불가능 |
| 이력을 언제부터 남기나 | `event_log`, `entitlement_adjustment` | **backfill이 원리적으로 불가능.** 늦게 만들면 그 이전 기간은 영구 공백. 마이그레이션 난이도는 0인데 비가역성은 최고 |
| 카운터인가 계산인가 | `class.taken` 컬럼 | 동시성 전략 전체가 여기 걸린다 |
| 생성 시점 스냅샷 | `class.capacity`를 복사 | 복사하지 않으면 "그때 정원이 몇이었나"를 영원히 복원 못 함 |
| 시각 타입 | `timestamptz` | 이미 저장된 값이 어느 타임존 기준인지 DB가 모른다 |
| hard delete인가 soft delete인가 | soft delete (`status`, `canceled_at`, `revoked_at`) | 이 선택 때문에 일반 UNIQUE가 못 쓰이고 **부분 유니크 인덱스 3개**가 필요해졌다 |
| NOT NULL 컬럼을 언제 추가하나 | `booking.restored`는 PRD 5 | 빈 테이블이면 공짜, 데이터가 있으면 3단계 ([기능 0005 마이그레이션](../spec/booking/0005-cancel-deadline/prd.md) 참조) |
| 신원과 credential 분리 | `member.id`와 `member_link.token_hash`를 분리 | PK를 URL에 썼다면 유출 시 링크만 갈아끼울 수 없다 |
| 상태 값의 표현 | `text` + CHECK | PostgreSQL `enum`은 값 삭제 · 순서 변경이 사실상 불가능. text를 택한 이유 |

PK 타입(`bigserial`)은 이 목록에서 가장 덜 중요하다. 열린 질문 Q7을 참조한다.
