# 수강권 도메인 데이터 모델

> 1차 뼈대. 도메인 ERD는 2차에서 그린다. 전체 그림은 [컨텍스트 맵](../../architecture/context-map.md).

## 테이블

| 테이블 | 도입 기능 |
|---|---|
| `entitlement` | 0001 |
| `entitlement_adjustment` | 0003 |
| `subscription` | 미정 |

## 주의할 쿼리

### 수강권 window 비교

`entitlement.window_start/end`는 `date`인데 `class.starts_at`은 `timestamptz`다. 그냥 비교하면 DB 세션 타임존에 따라 자정 근처 수업의 판정이 흔들린다.

```sql
-- 세션 타임존에 기대지 않고 명시적으로 변환한다
(c.starts_at AT TIME ZONE 'Asia/Seoul')::date
  BETWEEN e.window_start AND e.window_end
```
