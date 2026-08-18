# 하네스 문서

이 디렉터리는 `beyond-may-be`에서 Claude Code와 Codex가 함께 사용하는
AI 작업 하네스 자체를 설명한다.

## 이 디렉터리에 두는 문서

- 하네스 규칙의 운영 및 변경 방법
- 보호 영역과 외부 작업의 안전 기준
- AI 도구별 연결 상태와 회귀 시험 기록

## 이 디렉터리에 두지 않는 문서

다음 문서는 AI 하네스만을 위한 정보가 아니므로 `docs/harness/`에 두지 않는다.

- 프로젝트 아키텍처
- 도메인 설계
- API 명세와 코딩 컨벤션
- 일반 개발 및 배포 절차
- 장애 대응과 운영 절차

이러한 문서는 적용 범위에 따라 `docs/` 루트 또는 `docs/architecture/`,
`docs/development/`, `docs/operations/` 같은 별도 디렉터리에 둔다.

## 문서 목록

- [변경 영향 지도](change-impact-map.md)
- [AI 에이전트 보호 영역·외부 작업 안전 정책](safety-policy.md)
- [Codex behavioral 검증 실행 설명서](behavioral-validation.md)
- [하네스 단계별 구축 로드맵](setup-roadmap.md)

## 문서 상태와 보존 범위

| 문서 | 상태 | 책임과 갱신 기준 |
|---|---|---|
| README.md | 현행 안내 | 하네스 문서 목록과 책임이 바뀔 때 갱신한다. |
| change-impact-map.md | 현행 운영 기준 | 중요한 변경의 유형별 정본·영향·검증·중단 조건을 관리한다. |
| safety-policy.md | 현행 정본 | 보호 영역과 외부 작업 승인 기준을 관리한다. |
| behavioral-validation.md | 현행 절차 | Codex 행동 시험 절차와 회귀 결과를 관리한다. |
| setup-roadmap.md | 동결된 역사 기록 | 2026-07-25까지의 구축 이력과 검증 근거를 보존하며 신규 진행 상태는 추가하지 않는다. |

현행 프로젝트 상태·실행 결과·제품 정책은 README.md와 docs/의 해당 정본을
따른다. 동결 문서의 과거 내용은 삭제하거나 최신 상태로 덮어쓰지 않고, 사실 오류나
링크 오류만 필요한 범위에서 바로잡는다.

## 검증

Windows PowerShell 5.1에서 기본 semantic 검증은 다음과 같이 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\harness\verify-harness.ps1
```

버전·환경·저장소 접근 권한·프로젝트 스킬 해시는 다음 doctor로 확인한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\harness\harness-doctor.ps1
```

doctor는 `build.gradle`과 Gradle Wrapper에 선언된 버전, 현재 PowerShell·Git·Java·Docker
환경, Git ignore·추적 정책과 현재 권한 상태, `.agents/skills/` 전체 파일의 SHA-256과
`.claude/skills/` 원본 연결을 읽기 전용으로 검사한다. `WARN`은 관리자 권한이나 작업
트리 변경처럼 실행을 막지 않는 상태이며, `FAIL`은 환경 정합성 문제다.

이 검사는 behavioral 시나리오를 실행하지 않는다. Codex 10개 행동 시험은 사용자가
요청한 경우에만 실행하며, required approval 1명 전환 직전에는 전체 통과가 필요하다.
실행과 판정 방법은 [behavioral 검증 실행 설명서](behavioral-validation.md)를 따른다.
기본 semantic 검증에는 제품 기능 ID, MVP 상태표, 제품 문서 링크와 anchor의
정합성 및 `.agents/skills/`와 `.claude/skills/`의 프로젝트 스킬 집합 일치 검사가
포함된다.

## 관리 원칙

- 하네스 문서는 프로젝트 문서의 내용을 복사하지 않고 필요한 문서를 연결한다.
- 현행 문서 사이에 같은 프로젝트 상태를 반복하지 않고 정본 링크를 사용한다.
- 하네스 전용 규칙과 일반 프로젝트 규칙을 섞지 않는다.
- 반드시 막아야 하는 행동은 가능한 범위에서 권한, 샌드박스, 훅 또는 CI로 보호한다.
  기술적 보호를 적용하지 않기로 결정했다면 문서에 강제 여부와 남은 위험을 명시하고
  행동 검증으로 보완한다.
- 변경 작성자는 영향 대상과 검증 결과를 기록하고, 검토자는 필요한 재검사를 확인한다.
- 저장소 Admin 또는 배포 책임자가 하네스 관리자 역할을 맡는다.
- 하네스 변경의 정본·영향 대상·검증 범위는 [변경 영향 지도](change-impact-map.md)를
  따른다.
