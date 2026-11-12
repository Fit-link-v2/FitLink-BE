---
id: BE-ADR-0017
status: accepted
date: 2026-11-12
deciders: [BE]
source: [BE-ADR-0011, BE-ADR-0012, BE-ADR-0016, biseo 백엔드 패키지 구조]
---

# 모듈 안은 역할별 폴더로 나누고, 다른 모듈에는 루트의 Facade 하나만 연다

## 맥락

BE-ADR-0011은 모듈 안을 `api` · `application` · `infra` 세 패키지로 나눴다. 기능이 늘면서 두 가지가 불편해졌다.

- 파일이 어떤 역할인지 폴더 이름으로 알기 어렵다. `application`에 유스케이스 · 도메인 값 · 예외 · 다른 모듈용 창구가 함께 있다
- `application` 전체가 다른 모듈에 열려 있어, 모듈 안에서만 쓰려던 메서드도 다른 모듈이 부를 수 있다. 모듈 안의 호출 방향(`api` → `application` → `infra`)은 검사하지 않고 리뷰로 지켰다(BE-ADR-0011 대가)

개발자가 익숙한 biseo 백엔드는 도메인 안을 `controller` · `dto` · `service` · `model` · `repository`로 나눈다. 다만 biseo는 MSA라 서버 사이의 벽이 네트워크이고, FitLink는 서버 하나라 모듈 사이의 벽이 Spring Modulith 테스트다. biseo의 **서버 하나 안의 구조**를 FitLink의 **모듈 하나 안에** 가져온다.

## 결정

1. 모듈 안은 역할별 폴더 한 단계로 나눈다. 모듈 하나의 파일이 40 ~ 50개를 넘으면 그때 기능 폴더(예: `schedule/slot`)를 한 단계 더 둔다

   | 폴더 | 맡는 것 |
   |---|---|
   | `controller` | HTTP 요청 · 응답 |
   | `dto` | HTTP 요청 · 응답 record. `controller`만 쓴다 |
   | `service` | 유스케이스, 트랜잭션 경계(`@Transactional`). `model`을 받고 돌려준다 |
   | `model` | 도메인 값(record · enum) |
   | `repository` | SQL. BE-ADR-0012의 "SQL은 `infra`에만"은 "SQL은 `repository`에만"으로 읽는다 |
   | `exception` | 모듈의 에러 코드와 예외 |
   | `client` · `security` · `config` | 외부 API 호출 · Security 설정과 필터 · 설정값. 필요한 모듈만 둔다 |

2. 다른 모듈은 **모듈 루트의 `{모듈}Facade` 하나만** 부른다
   - Spring Modulith는 모듈 루트 패키지의 public 타입만 밖에 연다. 하위 폴더는 모두 숨겨지므로 `@NamedInterface`를 쓰지 않는다
   - Facade는 로직을 두지 않고 `service`에 넘긴다. 같은 일을 모듈의 `controller`와 다른 모듈이 함께 쓰기 때문이다
   - Facade가 주고받는 값도 모듈 루트에 둔다. 받는 값은 `{모듈}{무엇}Command`, 돌려주는 값은 `{모듈}{무엇}Info`. `long` · `int`처럼 단순한 값은 그대로 쓴다. `model`을 그대로 내보내지 않는다
   - Facade 메서드가 10개를 넘으면 `{모듈}{영역}Facade`로 나눈다
3. 여러 모듈이 같이 쓰고 도메인 규칙이 없는 코드는 최상위 `common` 모듈에 둔다
   - `@ApplicationModule(type = OPEN)`으로 하위 폴더까지 연다
   - 넣을 수 있는 것: 시계 설정, 에러 응답 모양과 공통 에러 처리, 로그인 강사 id 꺼내기. 이 목록 밖의 것은 넣지 않는다
   - `common`은 다른 모듈에 의존하지 않는다
4. Security 설정과 필터는 `auth` 모듈 안(`auth/security`)에 둔다. 세션 필터를 다른 모듈에 열 필요가 없어진다
5. 호출 방향은 리뷰가 아니라 테스트로 지킨다
   - 모듈 사이: Spring Modulith `verify()`(BE-ADR-0011)
   - 모듈 안: ArchUnit 테스트(`ArchitectureTests`). `controller` → `service` → `repository` 방향, `dto`는 `controller`만, Facade는 다른 모듈의 `service`만 부름, `common`은 다른 모듈을 모름, 모듈 루트에는 모듈 이름으로 시작하는 `Facade` · `Command` · `Info`만, 역할 폴더끼리 순환 금지
6. 여러 모듈의 테이블을 한 트랜잭션에서 바꾸는 동작(예약 · 휴강)은 가장 바깥 모듈의 `service`가 순서를 쥐고 다른 모듈의 Facade를 차례로 부른다. 구체적인 방식은 기능 0003 plan에서 정한다

이 결정이 대체하는 것:

| 결정 | 대체되는 부분 |
|---|---|
| BE-ADR-0011 | 결정 3(`api` · `application` · `infra` 표), 결정 4의 `application` `@NamedInterface` 공개, 대가의 "모듈 안 방향은 리뷰로 지킨다" |
| BE-ADR-0012 | 결정 2의 패키지 이름(`infra` → `repository`, `application` → `service`). 규칙 자체는 그대로다 |
| BE-ADR-0016 | 결정 4의 "`instructor`의 `application`을 부른다"(→ `InstructorFacade`), 결정 5(세션 필터 공개) |

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| `api` · `application` · `infra` 유지 + ArchUnit만 추가 | 비용이 가장 작다. 폴더 이름으로 역할이 드러나지 않는 불편은 그대로다 |
| 기능 폴더 먼저(`schedule/slot/controller`) | 지금 schedule은 클래스가 20개 남짓이라 파일 1 ~ 2개짜리 폴더가 많아진다 |
| 다른 모듈용 창구를 `api` 하위 폴더에 두고 `@NamedInterface` | 패키지 단위 `@NamedInterface`는 그 패키지 안의 타입만 연다. 창구가 주고받는 타입을 따로 열어야 하고, `api`가 지금(HTTP)과 반대 뜻이 된다 |
| 앱 전체 에러 코드 enum 하나(biseo `OperationsErrorCode`) | biseo에서는 그 enum이 서버 하나의 것이다. FitLink에서 서버 하나에 해당하는 것은 모듈이므로 에러 코드도 모듈마다 둔다 |

## 대가

- 폴더가 갈라져 package-private으로 숨기던 클래스(`ApiFormats` · `AuthCookies` 등)가 public이 된다. 모듈 밖은 Modulith가, 모듈 안 방향은 ArchUnit이 대신 막는다. 같은 역할 폴더 안의 호출(예: 한 `service`가 다른 `service`를 부름)은 막지 않는다
- Facade가 `model`을 `Info`로 바꾸는 변환 코드가 생긴다
- 문서(아키텍처 개요, 모듈 설명)의 패키지 이름을 함께 바꾼다
