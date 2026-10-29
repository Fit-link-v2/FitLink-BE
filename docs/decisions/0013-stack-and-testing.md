---
id: BE-ADR-0013
status: accepted
date: 2026-10-29
deciders: [BE]
source: [BE-ADR-0006, BE-ADR-0012]
---

# 기술 스택은 Java LTS · Spring Boot · Spring MVC · Flyway, 테스트는 Testcontainers로 실제 PostgreSQL에 대해 한다

## 맥락

Spring 프로젝트를 만들기 전에 언어 · 프레임워크 버전, 웹 스택, 스키마 관리, 테스트 도구를 정해야 한다. 설계는 PostgreSQL 전용 기능(부분 인덱스, EXCLUDE, `btree_gist`)에 기대므로 다른 DB로 대신 테스트할 수 없다.

## 결정

| 항목 | 선택 |
|---|---|
| 언어 | Java. 프로젝트를 만드는 시점의 최신 LTS |
| 프레임워크 | Spring Boot. 그 시점의 최신 GA. 실제 버전은 빌드 파일이 기준이고, 셋업 때 Spring Modulith · Testcontainers와의 호환을 확인한다 |
| 웹 | Spring MVC(서블릿, 요청당 스레드) |
| 스키마 | Flyway. 개발 PRD의 마이그레이션 번호를 파일 이름 `V{번호}__{설명}.sql`에 그대로 쓴다. 적용한 파일은 고치지 않고 새 번호를 만든다 |
| 데이터 클래스 | Java `record`. Lombok은 쓰지 않는다 |
| 테스트 | JUnit 5 + AssertJ. DB가 닿는 테스트는 Testcontainers로 띄운 PostgreSQL에 대해 돈다. API 테스트는 MockMvc |
| 시각 | 애플리케이션 코드는 `LocalDate.now()` · `Instant.now()`를 직접 부르지 않고, 빈으로 주입한 `Clock`에서 읽는다 |

- 테스트 이름은 개발 AC ID로 시작한다([docs/README.md](../README.md))
- 격리 테스트 데이터에는 강사를 2명 이상 만든다([컨텍스트 맵](../architecture/context-map.md))

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| Kotlin | 문법 · 도구를 같이 배워야 한다. Spring 학습 자료와 국내 실무 코드는 Java가 더 많다 |
| Spring WebFlux | 요청이 적고 DB 호출이 대부분이다. JDBC는 블로킹이라 WebFlux의 이점이 없고, 리액티브 모델 학습 비용만 생긴다 |
| Liquibase | XML · YAML로 변경을 적는다. 설계 문서가 이미 SQL DDL이라 SQL 파일을 그대로 쓰는 Flyway가 맞다 |
| JPA `ddl-auto` | 운영 스키마를 코드가 추측해 바꾼다. JPA를 쓰지 않는다(BE-ADR-0012) |
| H2 메모리 DB로 테스트 | EXCLUDE · 부분 인덱스 · `ON CONFLICT ... WHERE`를 지원하지 않거나 다르게 동작한다. 테스트가 통과해도 운영에서 실패할 수 있다 |
| Lombok | `record`로 대부분 대신할 수 있다. 컴파일 시점 코드 생성이 하나 줄어든다 |

## 대가

- Testcontainers는 테스트 실행 환경에 Docker가 필요하다. CI에도 Docker가 있어야 한다
- 컨테이너를 띄우는 시간만큼 테스트가 느리다. 컨테이너를 테스트 클래스 사이에 재사용하도록 설정한다
- SQL 안의 `now()`는 BE-ADR-0006대로 DB 시각을 쓰므로 `Clock`으로 고정되지 않는다. 애플리케이션이 계산한 `$today`를 넘기는 쿼리와 `now()`를 쓰는 쿼리가 한 흐름에 섞이면 테스트에서 두 시각이 어긋날 수 있다. 쿼리마다 어느 쪽을 쓸지는 기능의 plan 단계에서 정한다
- 최신 LTS · GA를 쓰므로 라이브러리가 새 버전을 아직 지원하지 않을 수 있다. 셋업 때 확인하고 안 되면 한 단계 아래 버전을 쓴다
