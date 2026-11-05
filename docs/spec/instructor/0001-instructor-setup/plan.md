# BE-0001 구현 플랜 · 강사 세팅

근거: [prd.md](prd.md) · [ac.yml](ac.yml)

## 개요

| Phase | User Story | AC | 상태 |
|---|---|---|---|
| 1 | PRD-0001 US 1 가입 · 로그인 · 세션 | AC-1 ~ AC-10 | 진행 중 (1.10 완료, 끝난 뒤 확인 남음) |
| 2 ~ | US 2 ~ 5 시간표 · 회원 · 수강권 · 링크 | AC-11 ~ (추가 예정) | 열린 질문 해소 후 작성 |

### Phase 1 구조

```mermaid
flowchart TB
    subgraph config["config"]
        SC["SecurityConfig<br/>필터 체인 · 허용 규칙"]
        CF["CsrfHeaderFilter<br/>X-CSRF: 1 검사"]
    end
    subgraph auth["auth 모듈"]
        subgraph aapi["api"]
            AC["KakaoAuthController<br/>GET /auth/kakao · /callback"]
            LC["LogoutController<br/>POST /auth/logout"]
            SF["SessionAuthenticationFilter<br/>쿠키 → 현재 강사"]
        end
        subgraph aapp["application"]
            LS["LoginService<br/>카카오 조회 + 가입 요청 + 세션 발급"]
            SS["SessionService<br/>확인 · 연장 · 로그아웃"]
            KP["KakaoOAuthClient (interface)"]
        end
        subgraph ainfra["infra"]
            SR["SessionRepository<br/>auth_session SQL"]
            KC["KakaoRestClient<br/>RestClient로 카카오 호출"]
        end
    end
    subgraph instructor["instructor 모듈"]
        MC["MeController<br/>GET /me"]
        IS["InstructorService<br/>가입 또는 조회 · 프로필"]
        IR["InstructorRepository<br/>instructor · setting SQL"]
    end
    SC --> SF
    SC --> CF
    AC --> LS
    LC --> SS
    SF --> SS
    LS --> KP
    KC -. 구현 .-> KP
    LS --> IS
    LS --> SR
    SS --> SR
    MC --> IS
    IS --> IR
    IR --> DB[("PostgreSQL")]
    SR --> DB
    KC --> K["카카오 API"]
```

- 로그인 · 세션 코드는 `auth` 모듈에 둔다([BE-ADR-0016](../../../decisions/0016-auth-module.md)). 1.1 ~ 1.6은 `instructor`에 만들었다가 1.6 뒤에 옮겼다
- 의존 방향은 `config → auth → instructor` 한쪽이다. `auth`는 가입할 때 `instructor`의 `application`(`InstructorService.signUpOrFind`)을 부른다
- `MeController`는 `auth`를 부르지 않는다. 필터가 SecurityContext에 남긴 강사 id를 `@AuthenticationPrincipal Long`으로 받는다
- `config`가 필터를 끼울 수 있도록 `auth`는 `SessionAuthenticationFilter` 하나만 `@NamedInterface("session-filter")`로 공개한다
- 카카오 호출은 `application`의 인터페이스(`KakaoOAuthClient`) 뒤에 둔다. 흐름 테스트는 가짜 구현으로 바꿔 끼우고, 실제 HTTP는 1.8에서 따로 검증한다

### 설정값

`application.yml`에 `fitlink.auth.*`로 둔다. 비밀값은 환경변수로만 받는다.

| 키 | local 값 | 비고 |
|---|---|---|
| `fitlink.auth.kakao.client-id` | `${KAKAO_CLIENT_ID}` | REST API 키 |
| `fitlink.auth.kakao.client-secret` | `${KAKAO_CLIENT_SECRET}` | 로그 금지(AC-10) |
| `fitlink.auth.kakao.redirect-uri` | `http://localhost:8080/auth/kakao/callback` | |
| `fitlink.auth.success-url` · `failure-url` | `http://localhost:5173/` · `http://localhost:5173/?login=failed` | Q30 임시값 |
| `fitlink.auth.cookie.session` · `cookie.state` | `SESSION` · `KAKAO-STATE` | dev · prod는 `__Host-Http-` 접두사 |

### 테스트 방식

- DB가 닿는 테스트는 공통 기반 클래스 하나(Testcontainers + MockMvc + 가짜 `KakaoOAuthClient`)를 상속한다. 클래스마다 설정을 바꾸지 않아 Spring 컨텍스트 · 컨테이너를 재사용한다
- 테스트 사이 데이터는 매 테스트 전에 Phase 1 테이블을 `TRUNCATE ... RESTART IDENTITY CASCADE`로 비운다. AC-3 동시성 테스트가 `@Transactional` 롤백과 맞지 않아서다
- 14일 경과(AC-7)는 `expires_at`을 SQL로 과거로 바꿔 만든다(prd 3.3 시각)

## Phase 1: PRD-0001 US 1 — 가입 · 로그인 · 세션

```mermaid
flowchart LR
    T11["1.1 스키마"] --> T12["1.2 가입"] --> T13["1.3 세션 저장소"]
    T13 --> T14["1.4 /me · 401"] --> T15["1.5 세션 필터 · 연장"]
    T15 --> T16["1.6 로그아웃 · CSRF"]
    T12 --> T17["1.7 로그인 시작"] --> T18["1.8 카카오 클라이언트"] --> T19["1.9 콜백"]
    T16 --> T19
    T19 --> T110["1.10 비밀값 로그"]
```

### 1.1 스키마 (V1)

- [x] RED: 실패하는 테스트 — `[BE-0001 AC-3] 같은 provider · provider_user_id 두 번째 INSERT는 instructor_identity_uk 위반`
- [x] GREEN: `V1__instructor.sql`에 `instructor` · `setting` · `instructor_session`(prd 3.1 DDL 그대로). auth 모듈 분리 때 세션 테이블을 `V2__auth_session.sql`로 옮기고 이름을 `auth_session`으로 바꿈
- [x] REFACTOR: 공통 테스트 기반 클래스 · TRUNCATE 정리 도입

### 1.2 가입 또는 조회 (InstructorRepository · InstructorService)

- [x] RED: `[BE-0001 AC-2] 처음 보는 카카오 번호는 instructor 1행과 setting(open_range_days=14) 1행을 만든다`
- [x] RED: `[BE-0001 AC-2] 닉네임 · 이메일이 없어도 가입하고 name · email은 null`
- [x] RED: `[BE-0001 AC-3] 같은 번호로 다시 가입 처리하면 같은 id, 행 수 그대로`
- [x] RED: `[BE-0001 AC-3] 같은 번호의 가입 처리 두 개를 동시에 실행해도 instructor · setting 1행씩`
- [x] GREEN: `INSERT ... ON CONFLICT ON CONSTRAINT instructor_identity_uk DO NOTHING RETURNING id` → 1행이면 `setting` INSERT, 0행이면 조회. 한 트랜잭션
- [x] REFACTOR: 카카오 사용자 정보를 받는 record(`KakaoUser(id, nickname, email)`) 정리 — GREEN에서 입력 타입으로 바로 도입

### 1.3 세션 저장소 (SessionRepository)

- [x] RED: `[BE-0001 AC-4] 발급한 세션 ID는 32바이트 난수이고 DB에는 SHA-256 해시만 저장된다`
- [x] RED: `[BE-0001 AC-7] 확인하면 expires_at이 now + 14일로 연장되고 instructor_id를 돌려준다`
- [x] RED: `[BE-0001 AC-7] expires_at이 지난 세션은 확인 결과가 비어 있다`
- [x] RED: `[BE-0001 AC-8] revoke하면 revoked_at이 채워지고 이후 확인 결과가 비어 있다`
- [x] GREEN: prd 3.1 로그인 세션 절의 조건부 UPDATE · revoke SQL
- [x] REFACTOR: 해시 계산을 한 곳(`SessionToken`)으로

### 1.4 `GET /me` 응답 모양과 401

- [x] RED: `[BE-0001 AC-6] 쿠키 없이 GET /me는 401 { code: UNAUTHENTICATED }`
- [x] RED: `[BE-0001 AC-6] DB에 없는 쿠키 값이면 401`
- [x] GREEN: Security entry point가 401 본문 `{ "code": "UNAUTHENTICATED", "message": ... }`을 쓴다
- [x] REFACTOR: 에러 본문 record(`ErrorResponse(code, message)`)를 공통으로

### 1.5 세션 확인 필터와 연장

- [x] RED: `[BE-0001 AC-6] 유효한 세션 쿠키로 GET /me는 200 { id, name, email }`
- [x] RED: `[BE-0001 AC-7] 인증된 요청 뒤 expires_at이 요청 시각 + 14일`
- [x] RED: `[BE-0001 AC-7] 만료된 세션 쿠키로 GET /me는 401`
- [x] GREEN: `SessionAuthenticationFilter`(쿠키 → `SessionService` → SecurityContext에 강사 id), `MeController`
- [x] REFACTOR: 컨트롤러가 현재 강사 id를 받는 방식(`@AuthenticationPrincipal` 또는 argument resolver) 하나로 정하고 Modulith 공개 범위 정리. `ModularityTests` 통과 확인 — `@AuthenticationPrincipal Long`, 필터 클래스에만 `@NamedInterface("session-filter")`

### 1.6 로그아웃과 CSRF 헤더

- [x] RED: `[BE-0001 AC-8] POST /auth/logout은 204, 쿠키 Max-Age=0, 세션 행 revoked_at 채움`
- [x] RED: `[BE-0001 AC-8] 로그아웃한 쿠키로 GET /me는 401`
- [x] RED: `[BE-0001 AC-8] 쿠키 없이 로그아웃해도 204`
- [x] RED: `[BE-0001 AC-9] X-CSRF 헤더 없는 로그아웃은 403 CSRF_HEADER_MISSING이고 세션은 그대로`
- [x] RED: `[BE-0001 AC-9] 인증이 필요한 경로에 세션 · 헤더 없이 POST하면 401`
- [x] GREEN: `LogoutController`, `CsrfHeaderFilter`(AuthorizationFilter 뒤)
- [x] REFACTOR: 쿠키 생성 · 삭제를 한 곳(`AuthCookies`)으로. 이름은 설정값 — 세션 필터를 Bean으로 바꾸고 서블릿 자동 등록을 끔. `config`는 `SessionService`를 직접 참조하지 않음

### 1.7 로그인 시작 `GET /auth/kakao`

- [x] RED: `[BE-0001 AC-1] 302 Location이 kauth.kakao.com/oauth/authorize이고 client_id · redirect_uri · response_type=code · state를 가진다`
- [x] RED: `[BE-0001 AC-1] KAKAO-STATE 쿠키가 Location의 state와 같고 HttpOnly · Secure · SameSite=Lax · Path=/ · Max-Age=600`
- [x] RED: `[BE-0001 AC-1] 두 번 호출하면 state가 다르다`
- [x] GREEN: `KakaoAuthController.start`, `AuthCookies.state`, `GET /auth/kakao` 허용 규칙
- [x] REFACTOR: 설정 record(`AuthProperties`)로 묶기 — 모듈 최상위 패키지에 둠(api · infra가 같이 씀). 카카오 키는 `.env`(git 제외)나 환경변수로 받음

### 1.8 카카오 클라이언트 (KakaoRestClient)

- [x] RED: `[BE-0001 AC-2] code로 토큰 요청 → 사용자 조회 결과를 KakaoUser로 바꾼다` (MockRestServiceServer로 카카오 응답 고정)
- [x] RED: `[BE-0001 AC-2] kakao_account에 닉네임 · 이메일이 없으면 null`
- [x] RED: `[BE-0001 AC-5] 토큰 · 사용자 조회가 4xx · 5xx면 정해진 예외 하나로 바꾼다`
- [x] GREEN: `RestClient`로 `POST kauth.kakao.com/oauth/token`(form: grant_type · client_id · client_secret · redirect_uri · code), `GET kapi.kakao.com/v2/user/me`
- [x] REFACTOR: 타임아웃 설정(연결 3초 · 응답 5초), 응답 JSON record 정리 — Spring Boot의 RestClient 자동 구성 모듈이 없어 `RestClient.builder()`를 직접 쓴다. 테스트는 MockRestServiceServer를 묶은 builder를 생성자로 넘긴다

### 1.9 콜백 `GET /auth/kakao/callback`

- [x] RED: `[BE-0001 AC-4] 성공하면 302 성공 주소, SESSION 쿠키, 세션 행, KAKAO-STATE 삭제`
- [x] RED: `[BE-0001 AC-4] 요청 파라미터로 목적지를 바꿀 수 없다`
- [x] RED: `[BE-0001 AC-4] 이전 SESSION 쿠키를 들고 로그인하면 새 값이 다르고 이전 세션은 revoked`
- [x] RED: `[BE-0001 AC-5] error=access_denied면 302 실패 주소, 세션 · 강사 행 없음`
- [x] RED: `[BE-0001 AC-5] KAKAO-STATE 쿠키가 없거나 state가 다르면 302 실패 주소`
- [x] RED: `[BE-0001 AC-5] 카카오 호출이 실패하면 302 실패 주소`
- [x] GREEN: `KakaoAuthController.callback` → `LoginService.login(code)`(가입 또는 조회 + 이전 세션 무효 + 새 세션, 한 트랜잭션) — 카카오 호출은 트랜잭션 밖(`TransactionTemplate`). state는 `MessageDigest.isEqual`로 비교. 테스트는 `@MockitoBean KakaoOAuthClient`
- [x] RED: `[BE-0001 AC-7] 인증된 요청은 SESSION 쿠키도 같은 값 · Max-Age 14일로 다시 준다`, `인증에 실패한 요청은 SESSION 쿠키를 다시 주지 않는다`
- [x] GREEN: 로그인 때 SESSION 쿠키 Max-Age 14일, 세션 필터가 인증 성공마다 같은 쿠키를 다시 발급 — DB 만료만 연장하면 활동 중이어도 로그인 14일 뒤 쿠키가 사라진다. 로그아웃 · 재로그인 응답에는 SESSION 쿠키가 하나뿐인지 테스트로 확인
- [x] REFACTOR: 실패 원인을 로그에 남기되 code · 토큰은 빼기 — `KakaoLoginException`은 원래 예외를 담지 않고 HTTP 상태만. `error` 파라미터는 카카오 에러 코드 형식일 때만 기록

### 1.10 비밀값이 로그에 없다

- [x] RED: `[BE-0001 AC-10] 로그인 · 재로그인 · 인증 · 로그아웃 흐름의 로그에 code · client secret · 세션 ID가 없다`, `카카오가 실패하고 에러 본문에 code · 토큰을 실어 보내도 로그에 없다` (`OutputCaptureExtension`, 우리 코드와 Spring 웹 · 보안 · JDBC logger를 TRACE로 낮춰 실행. 카카오 실패는 실제 `KakaoRestClient`를 가짜 카카오 서버에 붙임)
- [x] RED: `[BE-0001 AC-10] 세션 테이블에는 세션 ID 원본 컬럼이 없고 어느 테이블에도 카카오 토큰 컬럼이 없다`. 저장값이 쿠키 값의 SHA-256(32바이트)인 것은 1.3 · 1.9 테스트가 이미 확인
- [x] GREEN: 우리 코드의 로그는 걸리지 않았다. Spring logger 4개가 DEBUG · TRACE에서 code(요청 주소 · 컨트롤러 인자)와 client secret(카카오로 보내는 form)을 찍어 `application.yml`에서 INFO로 고정 — 상위 패키지 수준을 낮춰도 따라 내려가지 않는다. 4줄 각각 지우면 테스트가 실패하는 것 확인. MockMvc 테스트에 없는 Tomcat 요청 원문 logger(`Http11InputBuffer`)도 같이 고정. 목록 방식이라 운영에서 프레임워크 전체를 DEBUG로 내리지 않는다는 규칙을 설정 주석 · `overview.md`에 둠
- [x] REFACTOR: doc-sync — 기능 상태 in-progress, 강사 `POLICY.md` 규칙 절(가입 · 로그인 · 세션), `overview.md` 인증 흐름. `data-model.md`는 auth 모듈 분리 때 반영됨

## Phase 1 끝난 뒤 확인

- [ ] `./gradlew test` 전체 통과, `ModularityTests` 통과
- [ ] local에서 카카오 앱으로 실제 로그인: `/auth/kakao` → 카카오 → 콜백 → `/me` 200 → 로그아웃 → `/me` 401
