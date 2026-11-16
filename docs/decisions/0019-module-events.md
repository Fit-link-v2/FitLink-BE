---
id: BE-ADR-0019
status: accepted
date: 2026-11-16
deciders: [BE]
source: [BE-ADR-0011, BE-ADR-0017]
---

# 모듈 이벤트는 발행하는 모듈의 루트에 `{모듈}{무엇}Event` record로 두고, 받는 쪽은 같은 트랜잭션의 동기 리스너로 받는다

## 맥락

강사가 오픈 범위를 늘리면 같은 요청에서 늘어난 구간의 회차를 만들어야 한다. 설정은 `instructor`, 회차 생성은 `schedule` 모듈이다.

- `schedule`은 이미 `instructor`를 부른다(오픈 범위 일수 읽기). `instructor`가 `schedule`을 부르면 의존이 순환해 Spring Modulith 검사가 실패한다
- 나중에 월 정액 주기(`entitlement`)도 같은 시점에 만들어야 한다
- BE-ADR-0017 결정 2는 다른 모듈이 루트의 `{모듈}Facade` 하나만 부르고, 루트에는 `Facade` · `Command` · `Info`만 두게 했다. 이벤트를 받는 모듈은 Facade가 아닌 이벤트 타입에 의존해야 한다. `ArchitectureTests`도 그 밖의 이름을 막는다

## 결정

1. 다른 모듈에 알릴 일이 생긴 모듈은 Spring `ApplicationEventPublisher`로 이벤트를 발행한다. 발행하는 모듈은 받는 모듈을 모른다
2. 이벤트는 발행하는 모듈의 루트에 `{모듈}{무엇}Event` record로 둔다(예: `InstructorOpenRangeExtendedEvent`)
   - Spring Modulith가 루트의 public 타입만 밖에 열기 때문이다. 하위 폴더에 두면 컴파일은 되지만 모듈 경계 검사(`ModularityTests`)가 실패한다
   - `ArchitectureTests`의 모듈 루트 이름 규칙에 `Event`를 더한다
   - 받는 쪽이 같은 값을 쓰도록 필요한 값을 이벤트에 싣는다. 시각이 필요하면 발행 시점의 오늘 · 지금을 싣고 받는 쪽은 시계를 다시 읽지 않는다
3. 받는 쪽은 자기 모듈의 `service` 폴더에 `@EventListener`를 둔 클래스로 받는다
   - 동기 리스너라 발행한 트랜잭션 안에서 돈다. 리스너가 실패하면 예외가 발행한 쪽으로 올라가 함께 되돌아간다
   - 발행한 쪽 변경을 살리고 받는 쪽만 실패시켜야 하는 경우(예: 월 정액 주기의 건별 실패)는 받는 쪽이 savepoint로 다룬다
4. 커밋 뒤에 따로 돌아도 되는 일(알림 등)은 그 기능에서 정한다. 지금은 쓰지 않는다

이 결정이 대체하는 것:

| 결정 | 대체되는 부분 |
|---|---|
| BE-ADR-0017 | 결정 2의 "다른 모듈은 루트의 `{모듈}Facade` 하나만 부른다"에 "발행한 모듈 루트의 `{모듈}…Event`를 받는다"를 더한다. 결정 5의 루트 이름 규칙에 `Event`를 더한다 |

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| 받는 모듈이 조율: 설정 API를 `schedule`에 두고 `InstructorFacade`로 설정을 바꾼 뒤 회차를 만든다 | 강사 설정 API가 시간표 모듈에 생긴다. 설정 변경 때 할 일(월 정액 주기 등)이 늘 때마다 `schedule`이 그 모듈들을 모두 불러야 한다 |
| 이벤트를 하위 폴더(`instructor/event`)에 두고 `@NamedInterface`로 연다 | BE-ADR-0017이 `@NamedInterface`를 쓰지 않기로 했다. 공개 위치가 루트와 하위 폴더 두 곳이 된다 |
| Spring Modulith `@ApplicationModuleListener`(커밋 뒤 비동기 실행 + 이벤트 저장 테이블) | 회차 생성이 실패하면 설정 변경도 실패해야 한다(같은 트랜잭션). 이벤트 저장 테이블도 지금은 필요 없다 |

## 대가

- 호출 흐름이 이벤트에서 끊겨 보인다. 누가 받는지는 이벤트 타입의 사용처를 찾아야 안다
- 동기 리스너가 여럿이면 실행 순서를 보장하지 않는다. 서로 다른 테이블에 쓰는 리스너만 둔다. 순서가 필요해지면 다시 정한다
- 리스너의 실패가 발행한 요청을 실패시킨다. 무거운 일을 리스너에 넣으면 요청이 느려진다
