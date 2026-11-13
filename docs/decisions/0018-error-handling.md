---
id: BE-ADR-0018
status: accepted
date: 2026-11-12
deciders: [BE]
source: [BE-ADR-0017, biseo 백엔드 GlobalExceptionHandler]
---

# 에러 처리는 `common`의 처리기 하나가 맡고, 모듈은 에러 코드 enum만 둔다

## 맥락

에러 응답을 만드는 코드가 모듈마다 생기기 시작했다.

- `schedule`은 자기 컨트롤러에만 적용되는 처리기(`@RestControllerAdvice(basePackages = ...)`)와 응답 record, 예외 클래스 4개를 따로 두었다
- `auth` · `instructor` 컨트롤러의 예외는 이 처리기를 지나지 않아, 검증 실패나 405가 Spring 기본 응답 모양으로 나갔다
- 처리기가 받지 못한 예외는 `/error`로 다시 들어가 Spring 기본 500 응답이 됐다
- 남은 모듈(membership · entitlement · booking · analytics)도 같은 처리기를 하나씩 만들게 된다

BE-ADR-0017은 공통 에러 처리를 `common`에 둘 수 있게 했다.

## 결정

1. 예외는 `FitlinkException` 하나다. 무엇이 잘못됐는지는 예외 클래스가 아니라 **에러 코드**로 구분한다
   - 에러 코드는 `ErrorCode` interface(`code()` · `status()` · `message()`)를 구현한 enum이다. 응답 code는 상수 이름이고, `code()`는 interface의 기본 구현이 상수 이름을 돌려준다
   - 모듈마다 `{모듈}/exception/{모듈}ErrorCode` enum을 둔다. 어느 모듈에서나 쓰는 `VALIDATION_FAILED` · `NOT_FOUND` · `INTERNAL_ERROR`는 `common`의 `CommonErrorCode`에 둔다
   - 응답에 더할 값(겹친 요일 등)은 예외의 `details`에 담는다. 응답 본문에서 `code` · `message` 옆에 펼친다. `code` · `message`는 details 키로 쓰지 못한다
   - 요청을 거절하는 정상 흐름이므로 스택 트레이스를 기록하지 않는다
   - 필드 단위 입력 오류는 `InvalidRequestException(필드, 사유)`로 던진다. `VALIDATION_FAILED`와 `fields`를 채운 `FitlinkException`이다
2. 처리기는 `common/exception/GlobalErrorHandler` 하나이고, 범위를 제한하지 않아 모든 컨트롤러에 적용한다
   - Spring의 `ResponseEntityExceptionHandler`를 상속한다. Spring MVC 예외(검증 실패, 405, 415 등 약 20종)는 부모가 원래 상태 코드를 정하고, 우리는 응답 본문만 바꾼다
   - 400은 `VALIDATION_FAILED`, 404는 `NOT_FOUND`, 그 밖은 상태 이름(`METHOD_NOT_ALLOWED` 등)을 code로 쓴다
   - `VALIDATION_FAILED`는 언제나 `fields`를 둔다. 본문 필드, 경로 값 · 쿼리 파라미터 · 헤더 이름을 채우고, 알 수 없으면 빈 목록이다
   - 위에서 받지 못한 예외는 마지막 처리기(`Exception`)가 500 `INTERNAL_ERROR`로 응답한다. 원인은 응답에 담지 않고 로그에만 남긴다
   - Spring Security 예외(`AccessDeniedException` · `AuthenticationException`)는 다시 던져 Security 필터가 401 · 403으로 바꾸게 둔다
3. DB 제약 위반(`DataIntegrityViolationException`. 유니크 · CHECK · 외래 키)은 제약 이름으로 에러 코드를 고른다
   - 모듈은 자기 테이블의 제약 이름 → 에러 코드 대응을 `ConstraintErrors` 구현 bean으로 등록한다. `common`은 모듈을 모르고 bean 목록만 받는다
   - 제약 이름은 DB 에러 메시지에서 따옴표로 감싼 이름을 차례로 모듈에 물어 찾는다. 메시지 문구는 DB 언어 설정에 따라 바뀌어도 이름은 따옴표 안에 그대로 온다
   - 어느 모듈도 대응하지 않은 제약 위반은 코드 버그로 보고 500으로 둔다
4. 로그는 4xx를 warn, 5xx를 error로 남긴다. Spring MVC 예외의 4xx 로그에는 요청 값이 들어갈 수 있는 예외 메시지 대신 예외 종류와 주소만 쓴다
5. 필터는 컨트롤러 밖이라 처리기가 받지 못한다
   - 필터가 직접 거절하는 401 `UNAUTHENTICATED` · 403 `CSRF_HEADER_MISSING`은 `ErrorResponseWriter.write(응답, 에러 코드)`로 같은 모양의 본문을 쓴다. 이 코드는 `auth`의 `AuthErrorCode`에 둔다
   - 필터에서 처리하지 못한 예외는 서블릿 컨테이너가 `/error`로 보낸다. Spring Boot 기본 컨트롤러 대신 `ErrorPageController`가 같은 모양으로 응답한다
6. 응답 JSON 모양은 바꾸지 않는다. 카카오 로그인 실패(`KakaoLoginException`)는 컨트롤러가 잡아 로그인 실패 주소로 보낸다. 그 밖의 예외(DB 장애 등)는 콜백에서도 JSON 500이 된다

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| 모듈마다 처리기를 두고 `basePackages`로 범위 제한(지금 방식) | 모듈마다 같은 코드가 반복되고, 처리기가 없는 모듈의 에러는 Spring 기본 모양으로 나간다 |
| 예외 클래스를 사유마다 두기(`NotFoundException`, `SlotRejectedException` 등) | 사유가 늘 때마다 예외 클래스와 처리기 메서드가 함께 는다. 에러 코드 enum 상수 한 줄로 충분하다 |
| `ResponseEntityExceptionHandler` 없이 Spring 예외를 처리기 메서드로 하나씩 받기(biseo 방식) | 받지 않은 Spring 예외(경로 값 검사 실패 `HandlerMethodValidationException`, 필수 헤더 누락 등)가 마지막 처리기로 떨어져 400이어야 할 요청이 500 + error 로그가 된다. Spring이 예외를 추가할 때마다 따라 넣어야 한다 |
| 처리하지 못한 예외를 처리기에서 받지 않고 `/error`(Spring 기본)에 맡기기 | 응답 모양이 둘로 갈린다. 500의 로그 위치도 처리기와 서블릿 컨테이너 두 곳이 된다 |
| 응답을 RFC 9457 `ProblemDetail`(`type` · `title` · `status` · `detail`)로 바꾸기 | FE가 이미 `{ code, message }`를 쓴다. 바꿀 이유가 생기면 그때 새 결정으로 한다 |

## 대가

- 에러 코드의 상수 이름이 곧 API 응답이다. 상수 이름을 바꾸면 FE가 받는 code가 바뀐다. API 테스트가 code 문자열을 확인해 알아챈다
- enum마다 필드 · 생성자 · 메서드 세 개가 같은 모양으로 반복된다(Lombok 미사용, BE-ADR-0013)
- 제약 이름을 PostgreSQL 에러 메시지에서 꺼낸다. 마이그레이션에서 제약 이름을 바꾸면 대응표도 함께 바꿔야 한다. 제약 이름은 테이블 · 열 이름과 겹치지 않게 짓는다(지금 규칙: `_uidx` · `_uk` · `_fk` 등 접미사)
- 존재하지 않는 주소로 들어오는 요청(404)도 warn으로 남는다. 운영에서 많으면 404만 debug로 낮춘다
