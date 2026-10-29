---
id: BE-ADR-0012
status: accepted
date: 2026-10-29
deciders: [BE]
source: [BE-0001 기술 설계, BE-ADR-0001, BE-ADR-0009]
---

# 데이터 접근은 `JdbcClient`로 SQL을 직접 쓴다

## 맥락

설계 문서의 쓰기 경로는 PostgreSQL 기능에 기대고 있다.

- 조건을 WHERE에 넣은 조건부 UPDATE와 영향 행 수 판정(BE-ADR-0001)
- `INSERT ... ON CONFLICT DO NOTHING`, 부분 유니크 인덱스, EXCLUDE 제약(BE-ADR-0003 · BE-ADR-0009)
- `FOR NO KEY UPDATE` 행 잠금, `generate_series` · `LATERAL`로 주기 · 슬롯 생성

데이터 접근 방식을 정해야 한다. 국내 실무에서는 JPA(Hibernate)가 가장 많이 쓰인다. 이 프로젝트는 서비스 제공과 함께 BE 개발자의 학습 기회이기도 하다.

## 결정

1. Spring의 `JdbcClient`로 SQL을 직접 쓴다. 학습 목표가 "SQL과 트랜잭션이 DB에서 어떻게 동작하는지 직접 다루기"라서 이 방식을 골랐다
2. 나중에 JPA로 옮길 수 있도록 두 규칙을 지킨다
   - SQL은 모듈의 `infra` 패키지에 있는 Repository 클래스에만 둔다. `application`에는 SQL 문자열이 없다
   - Repository는 Java `record`를 받고 돌려준다. `ResultSet` · `Map` 같은 JDBC 타입이 Repository 밖으로 나가지 않는다
3. DB 제약 위반은 **제약 이름**으로 에러 코드에 대응한다. SQLState(23505 · 23P01)만 보면 안 된다. 같은 23505가 여러 유니크 제약에서 나기 때문이다. PostgreSQL 드라이버 예외의 서버 메시지에서 제약 이름을 읽어, 표에 있는 이름이면 해당 에러 코드로, 없으면 500으로 둔다

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| Spring Data JPA | 국내 실무 표준이라 학습 · 이직 가치가 가장 크다. 그러나 위 쓰기 경로 대부분이 native query가 된다. JPA를 배우면서 영속성 컨텍스트 · 지연 로딩 · flush 시점까지 같이 다뤄야 해 SQL 학습에 집중하기 어렵다 |
| MyBatis | SQL을 XML이나 애너테이션에 따로 둔다. 국내 SI · 금융권에서 많이 쓴다. `JdbcClient`로도 충분하고, 매퍼 설정이 하나 더 생긴다 |
| jOOQ | 타입 안전한 SQL을 쓸 수 있다. 스키마에서 코드를 생성하는 단계가 빌드에 들어가고, SQL 위에 jOOQ DSL을 하나 더 배워야 한다 |
| Spring Data JDBC | Aggregate 단위 저장을 전제로 한다. 전술적 DDD를 쓰지 않기로 해서(BE-ADR-0011) 맞지 않는다 |

## 대가

- 국내 채용 공고 대부분이 요구하는 JPA 경험이 이 프로젝트에서는 쌓이지 않는다. 필요해지면 위 2번 규칙에 기대어 모듈 하나씩 옮긴다
- 행을 `record`로 바꾸는 매핑 코드를 직접 쓴다. 컬럼을 더하면 SQL과 매핑을 함께 고쳐야 한다
- SQL 오타 · 컬럼 이름 오류는 실행해야 드러난다. Repository마다 Testcontainers로 실제 PostgreSQL에 대해 테스트한다(BE-ADR-0013)
