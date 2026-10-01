---
id: BE-ADR-0008
status: proposed
date: 2026-10-01
deciders: []
source: [DEC-0002]
---

# 강사 세션 저장소를 직접 만들지, Spring Session JDBC를 쓸지

## 맥락

DEC-0002는 "세션은 DB에 저장하고, 세션 ID는 해시로 저장한다"고 정했다.

Spring Session JDBC는 세션을 DB에 저장해 주는 공식 모듈이다. 그런데 공식 문서의 PostgreSQL 스키마는 `SPRING_SESSION` 테이블에 `SESSION_ID CHAR(36)`을 **원본 그대로** 저장한다. 그대로 쓰면 "해시로 저장"을 지킬 수 없다.

## 결정

아직 정하지 않았다. 설계 문서(BE-0001)는 직접 만드는 쪽의 테이블 `instructor_session`으로 적어 두었다.

## 검토한 대안

| 대안 | 얻는 것 | 치르는 것 |
|---|---|---|
| 직접 만든 `instructor_session` (현재 설계) | 세션 ID 해시 저장. 테이블 모양을 우리가 정한다 | 쿠키 발급 · 조회 · 연장 · 로그아웃 · 만료 정리를 직접 구현한다 |
| Spring Session JDBC | 세션 생성 · 만료 · 정리가 이미 구현돼 있다 | 세션 ID 원본 저장. DEC-0002의 "해시로 저장"을 바꾸거나 커스터마이즈해야 한다 |

## 대가

- 정하기 전까지 기능 0001의 plan 단계로 넘어갈 수 없다
