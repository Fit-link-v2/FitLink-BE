---
id: BE-ADR-0016
status: accepted
date: 2026-11-04
deciders: [BE]
source: [BE-ADR-0008, BE-ADR-0011, BE-ADR-0014]
---

# 로그인 · 세션 코드는 도메인 모듈과 따로 `auth` 모듈에 둔다

## 맥락

BE-ADR-0011은 도메인 6개를 모듈로 나눴고, 기능 0001 Phase 1(1.1 ~ 1.6)은 로그인 세션 코드를 `instructor` 모듈에 만들었다. 로그인하는 사람이 강사뿐이고 `instructor_session`이 `instructor`를 외래 키로 가리키기 때문이다.

남은 Phase 1 작업(카카오 로그인 시작 · 콜백 · 카카오 API 클라이언트)을 더하면 `instructor` 모듈의 절반 이상이 쿠키 · 필터 · OAuth 같은 인증 코드가 된다. 강사 도메인 규칙(가입 · 프로필 · 설정)과 인증 기술 코드가 한 모듈에 섞인다.

## 결정

1. 최상위 패키지 `com.fitlink.auth`를 모듈로 둔다. 도메인이 아닌 기술 모듈이며 `docs/spec`에 같은 이름의 폴더는 없다. 로그인 기능의 명세는 계속 기능 0001(`docs/spec/instructor/0001-instructor-setup`)에 둔다
2. `auth`가 맡는 것: 카카오 로그인 흐름, 세션 발급 · 확인 · 연장 · 로그아웃, 세션 쿠키, 세션 확인 필터, `instructor_session` 테이블(`V2__auth_session.sql`)
3. `instructor`가 맡는 것: 강사 가입 또는 조회(`InstructorService.signUpOrFind`), 프로필(`GET /me`), 설정
4. 의존 방향은 `auth → instructor`다. `auth`는 가입할 때 `instructor`의 `application`을 부른다. `instructor`와 다른 도메인 모듈은 `auth`를 부르지 않고, 필터가 SecurityContext에 남긴 강사 id를 `@AuthenticationPrincipal Long`으로 받는다
5. `config`가 Security 필터 체인에 끼울 수 있도록 `auth`는 세션 확인 필터 클래스 하나만 `@NamedInterface("session-filter")`로 공개한다
6. 테이블 이름 `instructor_session`은 바꾸지 않는다. 세션의 주인이 강사라는 뜻이고, 회원 로그인이 생기기 전까지 이름이 내용과 맞다

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| `instructor` 모듈에 그대로 둠 | 로그인 하는 사람이 강사뿐이라 데이터 주인으로 보면 맞다. 그러나 카카오 OAuth · 쿠키 · 필터가 들어오면 강사 도메인 코드보다 인증 코드가 커진다. 도메인 규칙을 찾을 때 인증 코드를 헤쳐야 한다 |
| `config`에 넣음 | `config`는 도메인이 아닌 설정만 두고 테이블을 갖지 않는다. 세션 테이블 · Repository가 들어가면 역할이 흐려진다 |
| 테이블 이름도 `auth_session`으로 바꿈 | 설계 결정 · PRD · 컨텍스트 맵이 `instructor_session`을 가리킨다. 이름을 바꿔 얻는 것이 없다 |

## 대가

- 모듈이 하나 늘어 `auth → instructor` 의존이 생긴다. `instructor`가 `auth`를 부르면 순환이 되므로, 강사 쪽에서 세션을 다룰 일(예: 탈퇴 시 세션 전체 무효)이 생기면 `auth`가 `instructor`의 이벤트를 받거나 `auth`의 API에서 시작하도록 방향을 정한다
- 회원 로그인이 생기면 `auth`가 두 종류의 주체를 다뤄야 한다. 그때 세션 테이블 구조를 다시 정한다
- 패키지 이름이 `docs/spec` 도메인 폴더 이름과 같다는 BE-ADR-0011의 규칙에 예외가 하나 생긴다
