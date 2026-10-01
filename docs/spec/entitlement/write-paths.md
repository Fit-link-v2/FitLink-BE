# 수강권 도메인 쓰기 경로

> 쓰기 흐름의 **현재 전체 모습**. 기능 PRD는 자기 변경분만 적고 여기로 링크한다. 기능이 머지될 때 doc-sync에서 이 문서를 고치고 변경 이력에 한 줄 추가한다.

## 1. 수강권 등록

> 변경 이력 (구현 전, 설계상 관련 기능): [0001](../instructor/0001-instructor-setup/prd.md)

횟수권이든 월 정액이든 등록은 한 트랜잭션이고, 시작할 때 그 회원 행을 잠근다.

```sql
BEGIN;

-- 같은 회원의 수강권 등록을 한 줄로 세운다.
SELECT id FROM member WHERE id = $member_id AND instructor_id = $me FOR NO KEY UPDATE;
-- 0행이면 남의 회원이거나 없는 회원. 롤백
```

`FOR NO KEY UPDATE`로 충분하다. 겹침 검사끼리만 한 줄로 세우면 되고, 회원 행의 키를 바꾸는 것이 아니기 때문이다. `FOR UPDATE`는 `FOR KEY SHARE` 잠금과도 충돌한다(PostgreSQL 문서 "Explicit Locking"의 행 잠금 충돌 표).

잠그는 이유는 종류 겹침 검사 때문이다. 강사가 두 탭에서 같은 회원에게 횟수권과 월 정액을 동시에 등록하면, 두 검사가 서로의 행을 보지 못한 채 둘 다 통과할 수 있다. 회원 행을 먼저 잠그면 두 번째 등록은 첫 번째가 끝날 때까지 기다린 뒤 검사한다.

### 1.1 횟수권

```sql
-- 월 정액 등록 기간 전체와 겹치는지 본다. 아직 만들어지지 않은 주기까지 포함한다 (PRD 1 AC 3.2.10)
SELECT 1 FROM subscription
 WHERE member_id = $member_id
   AND daterange(starts_on, ends_on, '[]') && daterange($window_start, $window_end, '[]');
-- 1행이라도 있으면 거부. 롤백

INSERT INTO entitlement (member_id, kind, window_start, window_end, max_count)
VALUES ($member_id, 'PASS', $window_start, $window_end, $max_count);

COMMIT;
```

같은 종류(횟수권)끼리는 검사하지 않는다. 겹쳐도 된다(AC 3.2.11).

### 1.2 월 정액

```sql
-- 이미 있는 횟수권과 겹치는지 본다
SELECT 1 FROM entitlement
 WHERE member_id = $member_id AND kind = 'PASS'
   AND daterange(window_start, window_end, '[]') && daterange($starts_on, $ends_on, '[]');
-- 1행이라도 있으면 거부. 롤백

INSERT INTO subscription (member_id, period_unit, count_per_period, starts_on, period_count, ends_on)
VALUES ($member_id, $period_unit, $count_per_period, $starts_on, $period_count, $ends_on)
RETURNING id;

-- 이어서 2의 생성 쿼리를 이 subscription 하나에 대해 돈다. 오픈 범위 안의 주기가 바로 생긴다

COMMIT;
```

`$ends_on`은 애플리케이션이 계산한다. 주 단위면 `starts_on + 7 × period_count - 1일`이다. 월 단위는 짧은 달 처리가 정해지지 않았다(BE-0001 열린 질문 Q12).

거부할 때의 에러 코드는 API 설계에서 정한다.

## 2. 주기별 수강권 생성

> 변경 이력 (구현 전, 설계상 관련 기능): [0001](../instructor/0001-instructor-setup/prd.md)

매일 1회 배치와 월 정액 등록 직후에 같은 쿼리를 돈다. 강사가 오픈 범위를 늘릴 때도 돌리는 것을 제안한다(BE-0001 Q19). 오늘부터 그 강사의 오픈 범위 끝까지에 걸치는 주기를 만든다(PRD 1 AC 3.2.8). 오픈 범위는 `[오늘, 오늘 + N일)`이다([BE-ADR-0006](../../decisions/0006-time-and-timezone.md)). 회원이 오픈 범위 안의 수업을 예약하려면 그 수업 날짜를 덮는 수강권 행이 이미 있어야 하기 때문이다.

```sql
-- 주 단위. $today = (now() AT TIME ZONE 'Asia/Seoul')::date
INSERT INTO entitlement (member_id, kind, window_start, window_end, max_count, source_id)
SELECT s.member_id, 'SUBSCRIPTION',
       p.start, LEAST(p.start + 6, s.ends_on),
       s.count_per_period, s.id
  FROM subscription s
  JOIN member  m  ON m.id = s.member_id
  JOIN setting st ON st.instructor_id = m.instructor_id
  CROSS JOIN LATERAL generate_series(0, s.period_count - 1) AS g(n)
  CROSS JOIN LATERAL (SELECT s.starts_on + 7 * g.n AS start) AS p
 WHERE s.id = $subscription_id                      -- 한 건씩 돈다
   AND s.period_unit = 'WEEK'
   AND p.start     <  $today + st.open_range_days   -- 오픈 범위 [오늘, 오늘+N) 안에서 시작
   AND p.start + 6 >= $today                         -- 이미 끝난 주기는 건너뜀
ON CONFLICT (source_id, window_start) WHERE source_id IS NOT NULL DO NOTHING;
```

- `generate_series(0, period_count - 1)`이라 등록한 주기 수를 넘는 주기는 만들어지지 않는다(AC 3.2.9)
- `ON CONFLICT ... DO NOTHING`과 부분 유니크 인덱스 `entitlement_period_uidx`로, 배치를 몇 번 돌려도 같은 주기가 두 번 생기지 않는다
- 월 단위는 같은 모양에서 `p.start`를 `starts_on`에 `n`개월을 더한 날로 바꾼다. 29~31일 시작의 처리가 정해지면 쿼리를 확정한다(Q12)

배치는 생성 대상 월 정액의 id 목록을 뽑은 뒤 **한 건씩 따로 트랜잭션으로** 이 쿼리를 돈다. `ON CONFLICT`가 받아 주는 것은 지정한 유니크 인덱스의 충돌뿐이고, 종류 겹침 EXCLUDE 위반은 그대로 에러가 되어 그 문장 전체가 실패한다(PostgreSQL 문서 INSERT의 conflict_target 설명). 한 문장으로 전체를 돌면 한 건의 위반이 모든 회원의 생성을 막는다. 실패한 건은 로그로 남긴다. 1의 등록 검사가 정상이라면 이 실패는 일어나지 않는다.

수강 종료(ENDED) 회원의 월 정액을 계속 만들지는 정하지 않았다. 중도 종료(BE-0001 Q13)와 같이 정한다.
