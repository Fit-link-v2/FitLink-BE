# FitLink BE

FitLink 백엔드 개발 레포. 개인 강사의 그룹 수업 예약 서비스의 서버 코드와 설계 문서를 함께 둔다.

- 스택: Java 25 · Spring Boot 4.1 · Spring Modulith, PostgreSQL 17 ([아키텍처 개요](docs/architecture/overview.md))
- 상태: 기능 0001 Phase 1(카카오 로그인 · 세션) 구현 중

## 구조

```
FitLink-BE/
  README.md
  build.gradle.kts            빌드 설정 (Gradle Kotlin DSL)
  compose.yaml                local 개발용 PostgreSQL
  src/main/java/com/fitlink/
    instructor/ schedule/ membership/ entitlement/ booking/ analytics/
                              도메인 모듈. 안은 controller · dto · service · model · repository · exception,
                              다른 모듈에는 루트의 {모듈}Facade만 연다
    auth/                     로그인 · 세션 · Spring Security 설정 (도메인이 아닌 기술 모듈)
    common/                   여러 모듈이 같이 쓰는 코드 (시계 설정, 에러 응답 모양과 공통 에러 처리)
  src/main/resources/
    application.yml           공통 설정
    application-local.yml     local 프로필 (compose.yaml의 DB)
    db/migration/             Flyway 마이그레이션 V{번호}__{설명}.sql
  docs/                       설계 문서. 도메인별 현재 모델 · 기능별 개발 PRD · 설계 결정
```

문서 읽는 법과 작성 규칙은 [`docs/README.md`](docs/README.md), 전체 목록은 [`docs/SUMMARY.md`](docs/SUMMARY.md)에 있다.

## 로컬 실행

필요한 것: JDK(아무 버전이나. 빌드가 JDK 25를 자동으로 내려받는다), Docker.

```bash
docker compose up -d                          # PostgreSQL (localhost:5433)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
curl -i localhost:8080/actuator/health        # 200
curl -i localhost:8080/me                     # 401 (로그인 전)
docker compose down                           # 종료. 데이터는 볼륨에 남는다. 지우려면 -v
```

## 테스트

```bash
./gradlew test
```

DB가 닿는 테스트는 Testcontainers가 PostgreSQL 컨테이너를 직접 띄운다. compose의 DB는 쓰지 않는다.

Docker Desktop이 아닌 Colima를 쓰면 Testcontainers가 Docker를 찾지 못한다. 셸 설정에 다음을 넣는다.

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## 관련 레포

| 레포 | 내용 |
|---|---|
| [Fit-link-PRD](https://github.com/Fit-link-v2/Fit-link-PRD) | 제품 요구사항(무엇을 만드는가)과 FE · BE 공통 결정 |
| [Fitlink-FE](https://github.com/Fit-link-v2/Fitlink-FE) | 프론트엔드 |
| [Fit-link-meeting-log](https://github.com/Fit-link-v2/Fit-link-meeting-log) | 회의 기록 |
