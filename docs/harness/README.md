# 하네스 문서

이 디렉터리는 `beyond-may-be`에서 Claude Code와 Codex가 함께 사용하는
AI 작업 하네스 자체를 설명한다.

## 이 디렉터리에 두는 문서

- 하네스의 완성 기준과 평가표
- 하네스 평가 결과와 개선 기록
- 하네스 규칙의 운영 및 변경 방법
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

- [하네스 완성 기준 및 평가표](completion-criteria.md)
- [하네스 운영과 유지관리 정책](completion-criteria.md#g6-운영과-유지관리)
- [AI 에이전트 보호 영역·외부 작업 안전 정책](safety-policy.md)
- [Codex behavioral 검증 실행 설명서](behavioral-validation.md)
- [하네스 단계별 구축 로드맵](setup-roadmap.md)

## 검증

Windows PowerShell 5.1에서 기본 semantic 검증은 다음과 같이 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\harness\verify-harness.ps1
```

이 검사는 behavioral 시나리오를 실행하지 않는다. Codex 10개 행동 시험은 사용자가
요청한 경우에만 실행하며, 하네스 완성 판정 직전에는 전체 통과가 필요하다.
실행과 판정 방법은 [behavioral 검증 실행 설명서](behavioral-validation.md)를 따른다.

## 관리 원칙

- 하네스 문서는 프로젝트 문서의 내용을 복사하지 않고 필요한 문서를 연결한다.
- 하네스 전용 규칙과 일반 프로젝트 규칙을 섞지 않는다.
- 반드시 막아야 하는 행동은 가능한 범위에서 권한, 샌드박스, 훅 또는 CI로 보호한다.
  기술적 보호를 적용하지 않기로 결정했다면 문서에 강제 여부와 남은 위험을 명시하고
  행동 검증으로 보완한다.
- 담당 역할, 갱신 조건과 재검사 범위는
  [운영과 유지관리 정책](completion-criteria.md#g6-운영과-유지관리)을 따른다.
