# Beyond May Be 지식 베이스

이 문서는 팀 개발자와 AI 에이전트가 현재 작업에 필요한 정본을 찾는 탐색 허브다.
상세 내용을 복사하지 않고, 질문별로 확인할 문서와 역할만 안내한다.

## 무엇을 확인하려는가

| 질문 또는 작업 | 정본 |
|---|---|
| 프로젝트의 목적과 로컬 실행·기본 검증 방법은 무엇인가? | [프로젝트 README](../README.md) |
| 전체 서비스가 목표로 하는 상세 기능과 예외 동작은 무엇인가? | [상세 기능 명세](product/feature-spec.md) |
| 현재 기준이 없거나 서로 충돌하는 제품 조건은 무엇인가? | [제품 논의 필요](product/open-questions.md) |
| 현재 백엔드가 책임지는 MVP 기능과 구현 상태는 무엇인가? | [백엔드 MVP 상태](product/mvp.md) |
| 사용자가 어떤 순서와 권한으로 서비스를 이용하는가? | [MVP 사용자 흐름](product/user-flow.md) |
| 백엔드 도메인의 책임과 관계는 무엇인가? | [백엔드 아키텍처](architecture/backend.md) |
| 현재 API 요청·응답 계약은 무엇인가? | 실행 중인 애플리케이션이 생성하는 OpenAPI. 접근 방법은 [README의 API 문서](../README.md#api-문서)를 따른다. |
| Controller·Service·Converter·DTO를 어떻게 작성하는가? | [API 계층 코딩 컨벤션](api-layer-convention.md) |
| 커밋 메시지를 어떻게 작성하는가? | [커밋 컨벤션](development/commit-convention.md) |
| 중요한 제품·구조 변경의 이유는 무엇인가? | [개별 ADR](adr/) |
| 변경이 여러 문서·코드·검증에 영향을 주는가? | [변경 영향 지도](harness/change-impact-map.md) |
| 운영 환경은 어떻게 배포·확인·복구하는가? | [AWS 배포·운영 절차](operations/deployment.md) |
| 공모전 데모를 어떻게 실행하고 복구하는가? | [공모전 데모 Runbook](demo/runbook.md) |
| AI 에이전트는 어떤 규칙과 검증 절차를 따르는가? | [AI 작업 규칙](../AGENTS.md) |
| 보호 영역과 외부 작업의 안전 기준은 무엇인가? | [AI 에이전트 안전 정책](harness/safety-policy.md) |
| 하네스의 상세 문서와 검증 방법은 어디에 있는가? | [하네스 문서 안내](harness/README.md) |
| 설치된 AI 스킬의 역할과 호출 시점은 어디에서 확인하는가? | [프로젝트 AI 스킬 사용 안내](harness/skill-catalog.md) |

## 정본 관리 원칙

- 정본은 변경할 수 없는 확정본이 아니라 현재 적용 중인 최신 기준이다.
- 관련 코드 변경과 정본 갱신은 같은 Pull Request에 포함하고 병합 전에 일치시킨다.
- 같은 내용을 여러 문서에 복사하지 않고, 정본을 링크한다.
- 중요한 변경은 변경 유형별 영향 지도를 확인하고, 변경한 대상·확인했지만 변경하지
  않은 대상·확인하지 못한 대상·미해결 항목과 검증 근거를 기록한다.
- 현재 적용할 기준이 없는 항목만 해당 문서의 `논의 필요` 영역에 기록한다.
- `docs/product/feature-spec.md`는 기능 영역별 상세 정본을 연결하는 대표 지도이고,
  `docs/product/features/`의 각 문서가 해당 영역의 상세 동작을 관리한다.
- `docs/product/mvp.md`는 백엔드 책임과 구현 상태를, GitHub Issues와 Project는
  개발 작업의 진행 상태를 관리한다.
- `.dev/`의 개인 설계·계획은 정본이 아니며, 지속해서 필요한 결론만 정본 문서로
  옮긴다.

| 설치 스킬의 중복과 재구성 방향 | [스킬 재구성 분석 기록](harness/skill-reorganization-analysis.md) |
