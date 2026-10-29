---
id: BE-ADR-0015
status: accepted
date: 2026-10-29
deciders: [BE]
source: [DEC-0007, DEC-0008, BE-ADR-0009]
---

# BE는 AWS Lightsail 1대에 Docker Compose(Caddy · Spring · PostgreSQL)로 올린다

## 맥락

첫 검증 규모는 강사 5명이다. 비용을 낮추고, BE 개발자가 서버 · DB 운영을 직접 해 보는 것도 목표다. 브라우저는 `api.{도메인}`을 직접 호출하고(DEC-0008), FE는 정적 파일로 따로 배포한다.

## 결정

### 구성

- AWS Lightsail 인스턴스 1대에 Docker Compose로 컨테이너 셋을 띄운다

  | 컨테이너 | 역할 |
  |---|---|
  | Caddy | HTTPS 종료(인증서 자동 발급 · 갱신), `api.` 요청을 Spring으로 넘김 |
  | Spring Boot | 애플리케이션 |
  | PostgreSQL | DB. 데이터는 볼륨에 둔다. 외부 포트를 열지 않는다 |

- Spring은 `server.forward-headers-strategy=framework`로 Caddy가 넘기는 `X-Forwarded-*` 헤더를 읽는다. 쿠키 `Secure`는 이 값에 기대지 않고 항상 켠다(BE-ADR-0014)
- Caddy access log에서 카카오 콜백의 `code` 쿼리 값을 가린다. Spring 쪽 access log는 켜지 않는다

### DB 백업

- `pg_dump`를 하루 여러 번 돌려 결과를 서버 밖(S3 등)에 둔다. 서버가 통째로 사라져도 마지막 백업 시점까지는 되살린다
- 복원을 실제로 연습한다. 백업 파일로 빈 DB를 만들어 애플리케이션이 뜨는지 확인한다

### 환경

| 환경 | 주소 | 용도 | DB |
|---|---|---|---|
| local | `localhost` | 개발. Docker Compose로 PostgreSQL을 띄운다 | 언제든 초기화 |
| dev | `api-dev.{도메인}` · `app-dev.{도메인}` | FE 연동 | 1차 배포 전까지는 초기화할 수 있다 |
| prod | `api.{도메인}` · `app.{도메인}` | 운영 | 초기화하지 않는다 |

- dev와 prod는 CORS 허용 출처 · 카카오 `redirect_uri` · 쿠키 이름을 각자 설정으로 갖는다. 한 환경의 값이 다른 환경에 섞이지 않게 한다(DEC-0008 CORS 절)

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| EC2 + RDS | 백업 · 장애 조치를 RDS가 해 준다. 같은 규모에서 비용이 더 들고, DB 운영을 직접 해 볼 기회가 없다. 규모가 커지면 이쪽으로 옮긴다 |
| Lightsail 관리형 DB | 백업은 맡길 수 있지만 인스턴스와 별도로 요금이 붙는다 |
| PaaS(Render · Railway · Fly.io 등) | 배포는 쉽다. 프록시 · 인증서 · 컨테이너 구성을 직접 다루지 않아 인프라 학습 범위가 줄고, 요금 체계가 서비스마다 바뀐다 |
| Nginx + certbot | 인증서 발급 · 갱신을 따로 구성해야 한다. Caddy는 설정 몇 줄로 자동이다 |

## 대가

- 서버 1대가 단일 장애점이다. 인스턴스가 죽으면 서비스 전체가 멈춘다
- DB 백업 · 복원 · 버전 업그레이드 · 디스크 관리를 직접 한다. 백업 주기 사이의 데이터는 서버를 잃으면 사라진다
- 배포 중 Spring 컨테이너를 바꾸는 동안 짧게 응답이 끊긴다
- 공식 PostgreSQL 이미지는 contrib 확장을 포함하므로 `btree_gist`(BE-ADR-0009)를 켤 수 있다. RDS로 옮길 때도 지원 확장인지 다시 확인한다
- HTTPS 인증서 발급에 도메인이 필요하다. 도메인(DEC-0007)을 사기 전에는 dev도 띄울 수 없다
- dev와 prod를 같은 인스턴스에 둘지는 walking skeleton을 만들 때 정한다
