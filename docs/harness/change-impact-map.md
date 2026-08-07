# 변경 영향 지도

이 문서는 기획·설계·코드·하네스가 함께 바뀔 때 먼저 확인할 정본과 검증 경로를
안내한다. 제품 규칙이나 API 상세를 복사하지 않으며, 현재 의미는 각 정본에서
판단한다.

## 사용 방법

1. 변경 유형을 하나 이상 선택한다.
2. `canonical_sources`를 먼저 읽는다.
3. `candidate_impacts`를 기준으로 저장소에서 feature ID, ADR ID, API 경로와 도메인명을
   검색해 영향 집합을 확정한다.
4. 변경한 대상, 확인했지만 변경하지 않은 대상, 확인하지 못한 대상과 미해결 항목을
   PR 또는 Issue에 기록한다.

오탈자, 내부 변수명 변경처럼 공개 의미와 정본에 영향을 주지 않는 작은 변경은 이
절차를 생략할 수 있다. 복수 유형의 변경은 각 유형의 영향 집합을 합친다. “영향 없음”은
전수 보장이 아니라 해당 대상과 변경 내용을 비교한 판단 근거를 뜻한다.

## 정본과 기록의 구분

| 정보 | 현재 정본 | 파생·기록 |
|---|---|---|
| 제품 행동·기능 의도 | [제품 기능 명세](../product/feature-spec.md), [기능별 상세](../product/features/common-policies.md) | [사용자 흐름](../product/user-flow.md) |
| 미확정 제품 정책 | [제품 논의 필요](../product/open-questions.md) | PR·Issue 논의 |
| 백엔드 책임과 현재 상태 | [백엔드 MVP 상태](../product/mvp.md) | README·기능 명세 |
| 목표 백엔드 구조 | [백엔드 아키텍처](../architecture/backend.md) | 코드·테스트 |
| 아키텍처 결정 | [개별 ADR](../adr/) | 관련 설계·코드 |
| 실제 API 계약 | `Controller`, DTO와 실행 시 생성되는 OpenAPI | [README API 안내](../../README.md), Swagger UI, `/v3/api-docs`, [Postman 안내](../../postman/README.md) |
| 하네스 규칙과 안전 | [AI 작업 규칙](../../AGENTS.md), [안전 정책](safety-policy.md) | [하네스 안내](README.md), [검증기](../../scripts/harness/verify-harness.ps1), CI |
| 변경 이력 | 저장소 정본 | PR·Issue·커밋 |

PR·Issue는 현재 사실을 보관하는 정본이 아니다. 기록이 닫혔거나 읽히지 않아도 현재
상태를 복원할 수 있도록 결정과 상태를 저장소 정본에 반영한다.

## 변경 유형별 영향

| change_type | trigger_examples | canonical_sources | candidate_impacts | required_checks | stop_conditions |
|---|---|---|---|---|---|
| `product` | 기능 행동, 사용자 역할, 정책, 성공 조건 변경 | `docs/product/feature-spec.md`, `docs/product/features/*.md`, `docs/product/open-questions.md` | `docs/product/mvp.md`, `docs/product/user-flow.md`, 관련 `Controller`·DTO·Service·테스트·Postman·아키텍처 문서 | 제품 ID·link·anchor 검사, 의미 검토, 관련 테스트, 필요한 수동 흐름 확인 | 정책 충돌, 미확정 요구사항, 승인 범위 초과 |
| `api` | endpoint, request/response, 오류, 상태 코드, 인증 계약 변경 | 실제 `Controller`·DTO, 실행 OpenAPI | 관련 기능 명세·`mvp.md`, `backend.md`, `docs/adr/`, Postman, API 테스트 | 컴파일, 관련 테스트, OpenAPI·Postman 예시 검토, 공개 의미 검토 | 계약 변경 의도 불명확, 관련 기능·아키텍처 영향 미확인 |
| `data` | Entity, 저장 형식, Repository, Flyway migration, 데이터 수명 변경 | `backend.md`, 관련 `docs/adr/`, 실제 Entity·migration·`application*.yml` | Repository·Service·테스트·API·기능 명세·안전 문서·운영 절차 | 아키텍처·애플리케이션 컨텍스트 테스트, 품질 검사·빌드, migration 적용 순서 검토 | 데이터 손실 위험, migration 범위 불명확, 운영 DB 영향 |
| `architecture` | 패키지·도메인 경계, 의존 방향, 배포 대상·인프라 구조, ADR 생성·수정·대체 | `docs/architecture/backend.md`, `docs/adr/` | 도메인 코드, Terraform·Docker, 배포 workflow, 운영 문서, ArchUnit 테스트, 제품 문서, `AGENTS.md`, `docs/harness/*`, `.agents/skills/`, `.claude/skills/` | `change-impact-review`, 결정 기록·코드·인프라·운영 흐름과 관련 테스트 의미 비교, ADR 영향 대상·미해결 항목 확인 | 기존 결정과 충돌, 목표와 현재 상태 혼동, 활성 하네스에 이전 결정 잔존, 큰 구조 변경 |
| `security` | 인증·인가, 개인정보, 비밀값, OIDC·외부 쓰기·자동 배포 승인, 보호 영역 변경 | `AGENTS.md`, `docs/harness/safety-policy.md`, 실제 보안·배포 설정 | `application*.yml`, Terraform IAM·SSM, 배포 workflow, 로그, README·runbook·운영 문서, 테스트 | 보호 경로 검사, 관련 테스트, 비밀값 노출·최소 권한·배포 승인 경계 검토 | 승인 없는 외부 쓰기, 비밀값 접근·출력, 보호 규칙 충돌 |
| `harness` | 공통 지침, skill, 검증 명령, CI·자동 배포 workflow, 회귀 기준, 문서 책임 변경 | `AGENTS.md`, 활성 `docs/harness/*`, 관련 `.agents/skills/` | 검증 scripts, 회귀 tests, README·docs index, PR template, CI·배포 workflow, ADR·운영 문서 | `verify-harness.ps1`, 관련 회귀 테스트, G6 재검사, 필요 시 전체 검증 | 규칙 충돌, 보호 정책 약화, 실행하지 않은 검증을 완료로 기록 |

## 검증 결과 기록

검증 결과는 다음 네 가지로 구분한다.

- `통과`: 명령 또는 의미 검토가 성공했다.
- `실패`: 검사 결과가 실패했으며 원인을 해결하거나 남은 문제로 기록해야 한다.
- `실행하지 못함`: 환경·권한·사전 조건 때문에 실행하지 못한 이유를 기록한다.
- `의미 검토 필요`: 구조 검사는 통과했지만 제품·설계·코드의 의미 일치를 추가로
  확인해야 한다.

자동 검사는 파일·ID·link·anchor·필수 유형 같은 객관적 구조를 확인한다. 문서가 코드보다
앞선 목표 상태인지, 실제 동작이 요구사항과 맞는지는 AI와 리뷰어가 영향 집합을 읽고
판단한다. 사람이 진행하는 데모는 기존 runbook으로 확인하며 자동 데모 runner를 추가하지
않는다.
