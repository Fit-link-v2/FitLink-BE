---
id: BE-ADR-0007
status: accepted
date: 2026-10-01
deciders: [BE]
source: [BE-0001 열린 질문 Q1]
---

# 슬롯 테이블 이름은 `class_slot`이다

## 맥락

슬롯(특정 날짜 · 시각의 수업 1회) 테이블을 `class`로 설계했다. `class`는 Java 예약어라 엔티티 클래스 이름으로 쓸 수 없고, `Class`는 `java.lang.Class`와 겹친다. mermaid erDiagram에서도 예약어로 걸려 그림에서만 `CLASS_SLOT`으로 적고 있었다.

## 결정

- 테이블 이름을 `class_slot`으로 한다. 외래 키는 `class_slot_id`
- PRD 문서의 물리 이름도 같이 바꾼다. 화면 용어 "슬롯" · "수업"은 그대로다
- 에러 코드 `CLASS_STARTED`는 API 계약 이름이라 바꾸지 않는다

## 검토한 대안

| 대안 | 버린 이유 |
|---|---|
| `class` 유지 + 엔티티만 다른 이름 | 테이블과 코드의 이름이 달라 검색 · 리뷰 때 헷갈린다 |
| `scheduled_class` | 뜻은 같고 더 길다 |

## 대가

- 없음. 구현 전이라 바꾸는 비용이 문서 수정뿐이다
