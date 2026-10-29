---
id: BE-ADR-0011
status: accepted
date: 2026-10-29
deciders: [BE]
source: [컨텍스트 맵]
---

# 서버 하나에 도메인 6개를 모듈로 나누고, Spring Modulith 테스트로 경계를 지킨다

## 맥락

코드를 처음 만들기 전에 서버를 몇 개로 나눌지, 코드를 어떤 단위로 묶을지 정해야 한다.

- 개발자는 BE 1명이다. 첫 검증 규모는 강사 5명이다
- 예약 한 건이 `class_slot` · `entitlement` · `booking`을 한 트랜잭션에서 바꾼다([예약 쓰기 경로](../spec/booking/write-paths.md)). 서버를 나누면 이 트랜잭션이 깨진다
- 설계 문서는 이미 도메인 6개(instructor · schedule · membership · entitlement · booking · analytics)로 나뉘어 있다([컨텍스트 맵](../architecture/context-map.md))

## 결정

1. **모듈러 모놀리스.** 배포 단위는 Spring Boot 애플리케이션 하나다. 그 안에서 도메인 6개를 최상위 패키지로 나눈다. 패키지 이름은 `docs/spec`의 도메인 폴더 이름과 같다
2. **DDD는 전략적 설계만 쓴다.** 도메인 경계(bounded context)와 도메인 사이 관계는 컨텍스트 맵을 따른다. 전술적 설계(Entity · Aggregate · Repository-per-Aggregate 규칙)는 쓰지 않는다. 쓰기는 Transaction Script로 한다. 서비스 메서드 하나가 트랜잭션 하나이고, 규칙은 SQL의 조건과 DB 제약에 둔다
3. **모듈 안은 세 레이어다.**

   | 패키지 | 맡는 것 | 부를 수 있는 것 |
   |---|---|---|
   | `api` | HTTP 요청 · 응답, 입력 검증, 에러 코드 변환 | 같은 모듈의 `application` |
   | `application` | 유스케이스. 트랜잭션 경계(`@Transactional`) | 같은 모듈의 `infra`, 다른 모듈의 `application` |
   | `infra` | SQL(Repository), 외부 API 클라이언트 | 없음 |

   - 다른 모듈은 상대 모듈의 `application`만 부른다. 상대의 `api` · `infra`는 부르지 않는다
   - 모듈 사이 의존 방향은 컨텍스트 맵의 화살표를 따른다. 순환을 만들지 않는다
4. **경계는 테스트로 강제한다.** Spring Modulith의 `ApplicationModules.of(...).verify()`를 테스트에 넣는다. 모듈 사이 순환이나 허용하지 않은 패키지 접근이 생기면 빌드가 실패한다
   - Spring Modulith는 모듈의 하위 패키지를 기본으로 내부(internal)로 본다. 각 모듈의 `application` 패키지에 `package-info.java`로 `@NamedInterface("application")`을 붙여 그 패키지만 외부에 연다

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| 도메인별 서버(MSA) | 예약 트랜잭션이 서버 여러 개에 걸친다. 분산 트랜잭션이나 보상 로직이 필요하고, 1명이 서버 6개를 배포 · 운영해야 한다 |
| 레이어 우선 패키지(`controller` · `service` · `repository`를 최상위에) | 도메인 경계가 코드에 드러나지 않는다. 어느 도메인이 어느 테이블을 건드리는지 패키지로 알 수 없다 |
| 전술적 DDD(Aggregate · 도메인 모델) | 규칙 대부분이 조건부 UPDATE · 부분 유니크 · EXCLUDE 같은 DB 제약으로 표현돼 있다. 같은 규칙을 객체 모델에 한 번 더 두면 두 곳을 맞춰야 한다 |
| 경계를 규칙 문서로만 지킴 | 리뷰에서 놓치면 그대로 들어간다. 테스트는 매 빌드에서 확인한다 |
| ArchUnit으로 직접 규칙 작성 | Spring Modulith가 모듈 탐지 · 순환 검사를 기본으로 준다. 레이어 규칙처럼 Modulith가 보지 않는 것은 필요해지면 ArchUnit을 더한다 |

## 대가

- 한 모듈이 느려지거나 장애가 나면 서버 전체가 영향을 받는다. 모듈별로 따로 늘릴 수 없다
- DB 하나를 모든 모듈이 같이 쓴다. 도메인을 넘는 외래 키(컨텍스트 맵)가 있어 나중에 서버를 나누려면 이 키를 먼저 풀어야 한다
- 예약처럼 여러 도메인 테이블을 한 트랜잭션에서 바꾸는 쓰기가 있다. 그 SQL을 어느 모듈에 두고 상대 모듈 `application`을 어떻게 부를지는 기능 0003의 plan 단계에서 정한다
- 휴강은 schedule의 API에서 시작해 booking의 예약을 취소한다. booking은 schedule에 의존하므로 반대 방향 호출이 생기면 순환이다. 호출 방향을 정하는 일은 휴강을 구현하는 기능의 plan 단계에서 한다
- `api` · `application` · `infra` 안의 방향(예: `api`가 `infra`를 직접 부름)은 Spring Modulith가 검사하지 않는다. 리뷰로 지킨다
