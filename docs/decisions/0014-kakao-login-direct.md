---
id: BE-ADR-0014
status: accepted
date: 2026-10-29
deciders: [BE]
source: [DEC-0001, DEC-0002, DEC-0008, BE-ADR-0008, PRD-0001 AC 1.1]
---

# 카카오 로그인은 Spring Security OAuth2 Client 없이 직접 구현한다

## 맥락

카카오 로그인 흐름은 DEC-0008에 있다. `state`를 짧은 수명의 쿠키에 두고, 콜백에서 비교한 뒤 인가 코드를 카카오 토큰으로 바꾸고, 사용자 정보로 강사를 가입 · 조회하고, 우리 세션 쿠키를 발급한다. 세션은 직접 만든 `instructor_session`에 해시로 저장한다(BE-ADR-0008).

Spring Security OAuth2 Client(`oauth2Login()`)는 이 흐름을 설정만으로 제공한다. 그러나 기본 동작이 우리 결정과 다르다.

- 인가 요청(`state` 포함)을 `HttpSession`에 저장한다. 우리는 `HttpSession`을 쓰지 않고 `state`를 쿠키에 둔다
- 로그인 결과(`Authentication`)를 `HttpSession`의 보안 컨텍스트에 저장한다. 우리는 `instructor_session` 행과 `__Host-Http-SESSION` 쿠키로 로그인을 표현한다

## 결정

1. 카카오 로그인을 컨트롤러 두 개(`GET /auth/kakao`, `GET /auth/kakao/callback`)와 카카오 API 클라이언트로 직접 구현한다. Spring Security OAuth2 Client는 쓰지 않는다
   - `/auth/kakao`: 난수 `state`를 만들어 `__Host-Http-KAKAO-STATE` 쿠키에 담고 카카오 인가 주소로 302
   - `/auth/kakao/callback`: 쿠키의 `state`와 비교하고 쿠키를 지운다 → 인가 코드로 토큰 요청 → 사용자 정보 요청 → 강사 가입 또는 조회 → 새 세션 발급 → 설정에 고정한 강사 화면 주소로 302
   - 카카오 토큰은 저장하지 않는다. 카카오 `code` · 토큰 · client secret은 로그에 남기지 않는다
2. 쿠키 규칙
   - `Secure`는 요청이 HTTPS였는지와 상관없이 항상 켠다. 앞단 프록시에서 HTTPS가 끝나므로 애플리케이션이 받는 요청은 HTTP다(BE-ADR-0015)
   - local 프로필은 쿠키 이름에서 `__Host-Http-` 접두사를 뺀다. `http://localhost`에서 접두사 쿠키가 거부된다는 보고가 있다(DEC-0008). dev · prod는 접두사를 쓴다
   - 쿠키 이름은 설정값으로 두고 코드에 박지 않는다

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| `oauth2Login()` + 커스터마이즈(쿠키 기반 `AuthorizationRequestRepository`, 성공 핸들러에서 우리 세션 발급, `HttpSession` 끄기) | 바꿔 끼워야 하는 부분이 흐름의 절반이다. 기본 동작과 우리 동작이 어디서 갈리는지 알아야 고칠 수 있어, 직접 구현보다 이해할 것이 많다 |
| `oauth2Login()` 기본 동작 그대로 | `HttpSession`이 생긴다. 세션을 해시로 저장한다는 DEC-0002 · BE-ADR-0008과 맞지 않는다 |

## 대가

- `state` 생성 · 비교, 토큰 교환, 에러 처리(`error=access_denied` 등)를 직접 구현하고 테스트해야 한다. 보안 실수를 프레임워크가 막아 주지 않는다
- 로그인 수단을 늘리면(DEC-0001의 `provider` CHECK 확장) 수단마다 같은 흐름을 다시 구현한다
- 카카오 API 변경(엔드포인트 · 응답 필드)을 직접 따라가야 한다
