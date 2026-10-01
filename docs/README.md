# FitLink BE 문서

> 레포 소개는 [루트 README](../README.md). 이 폴더는 개발 레포 안의 설계 문서 자리다.

백엔드의 **어떻게 만드는가(HOW)**를 다룬다. 무엇을 만드는지는 [Fit-link-PRD](https://github.com/Fit-link-v2/Fit-link-PRD)에 있다.

## 구조

```
docs/
  README.md                      이 문서
  SUMMARY.md                     전체 목차
  architecture/                  시스템 전체 그림
  decisions/                     BE 전용 설계 결정 (BE-ADR-NNNN)
  templates/                     문서 틀. recatch-tdd가 여기서 복사한다
  spec/
    {도메인}/                     ← 현재 모습. 코드가 바뀌면 같이 고친다
      POLICY.md                  코드가 지금 지키는 규칙 + 진행 중인 기능 표
      domain.yaml                소유 테이블과 도입 기능
      data-model.md              도메인 ERD · 테이블 · 주의할 쿼리
      write-paths.md             쓰기 흐름 (복잡한 도메인만)
      {NNNN-slug}/               ← 기록. 끝나면 동결한다
        feature.yaml             메타데이터: 원본 PRD, 상태, 참여 도메인
        prd.md                   개발 PRD. API 절 필수
        ac.yml                   AC 계약 (기계용)
        plan.md                  진행 체크박스
```

- 기능 폴더는 **담당 도메인 한 곳**에만 만든다. 같이 바뀌는 도메인은 `feature.yaml`의 `participating-domains`에 적고, 그 도메인 `POLICY.md`의 진행 중인 기능 표에 링크한다
- 후속 작업은 앞 폴더를 고치지 않고 새 번호 폴더를 만든다. `feature.yaml`의 `extends`에 앞 기능 번호를 적는다
- `openapi.json`은 문서가 아니라 빌드 결과물이라 `docs/` 밖에 둔다. 위치는 Spring 프로젝트 구성 때 정한다

## 흐름

recatch-tdd 흐름을 따른다. 단계마다 만들어지는 문서가 정해져 있다.

| 단계 | 산출물 | 비고 |
|---|---|---|
| prd | `{NNNN-slug}/prd.md` · `feature.yaml` | 담당 도메인 `POLICY.md`의 진행 중인 기능 표와 `SUMMARY.md`에 한 줄 추가 |
| ac | `{NNNN-slug}/ac.yml` | AC 계약. `source`에 근거 제품 AC |
| policy · policy-edge | 도메인 `POLICY.md`, `prd.md` 보강 | 빠진 규칙을 AC · 열린 질문 · 후속으로 분류 |
| plan | `{NNNN-slug}/plan.md` | Phase 1은 API 모양부터. 승인 후 `prd.md` · `ac.yml`을 baseline 커밋하고 **동결** |
| go | `plan.md` 체크박스 | 커밋마다 `[ ]` → `[x]`. 테스트 이름에 AC ID |
| pr | doc-sync | 아래 참조 |

### doc-sync (PR 직전)

- 기능 폴더: 구현과 어긋난 설명(API 응답 필드 등)만 고친다. 요구사항이 달라졌으면 자동으로 고치지 않고 승인을 받는다
- 도메인 층: `POLICY.md` · `data-model.md` · `write-paths.md`를 현재 모습으로 고치고, 변경 이력에 기능 링크를 한 줄 추가한다
- `feature.yaml`의 `status`를 갱신한다
- 커밋 메시지에 기능 번호를 넣는다. 예: `docs(booking): 0004 대기 승계 분기를 쓰기 경로에 반영`

머지 후(`status: done`) 기능 폴더는 고치지 않는다.

## ID · 이름 규칙

| 대상 | 형식 | 예 |
|---|---|---|
| 도메인 폴더 | 영문 단수 명사. 한 번 정하면 바꾸지 않음 | `booking` |
| 기능 폴더 | PRD 레포 폴더 이름 그대로 `{4자리}-{슬러그}` | `0003-booking` |
| 제품 AC | `PRD-{4자리} AC {번호}` | `PRD-0003 AC 1.3.1` |
| 개발 AC | `BE-{4자리} AC-{번호}` | `BE-0003 AC-2` |
| 결정 | BE 전용은 `BE-ADR-{4자리}`, FE와 공유는 PRD 레포 `DEC-{4자리}` | `BE-ADR-0001` |

테스트 이름은 개발 AC ID로 시작한다.

```java
@DisplayName("[BE-0003 AC-2] 정원이 찬 수업은 예약할 수 없다")
```

## 링크 규칙

| 방향 | 언제 | 형태 |
|---|---|---|
| 도메인 문서 → 기능 폴더 | 항상 | 절마다 "변경 이력" 목록 |
| 기능 PRD → 도메인 문서 | 항상 | 상대 링크. 최신 모습으로 열린다 |
| 기능 PRD → 도메인 문서 과거 버전 | 변경분만으로 이해가 안 될 때 | GitHub permalink (`blob/<커밋 해시>/경로`) |

- 제목 앵커(`#절-이름`) 대신 파일 단위로 링크한다. 제목을 고치면 앵커 링크가 조용히 깨진다
- 다른 레포는 절대 URL
