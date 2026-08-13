# 프로젝트 AI 스킬 사용 안내

이 문서는 프로젝트에 설치된 AI 스킬을 빠르게 선택하기 위한 지도다. 각 스킬의
전체 행동 규칙은 해당 `.agents/skills/<skill>/SKILL.md`가 정본이며, 이 문서는
목적·호출 시점·연결 관계만 요약한다.

## 현재 범위

- 프로젝트 원본 스킬: `.agents/skills/`
- Matt Pocock 원본 기반 스킬: `skills-lock.json`에 기록된 목록
- 프로젝트 전용 스킬: 원본 목록에 포함되지 않은 프로젝트 스킬
- Claude Code 연결 스킬: `.claude/skills/`. 각 연결은 `.agents/skills/` 원본을 따른다.
- `/grill`은 `.agents/skills/grill/` 원본과 `.claude/skills/grill/` 연결로 양쪽에서 사용한다.
- `harness-diagnostics`처럼 Codex 환경에 전역 설치된 스킬은 프로젝트 스킬 집합에
  복사하지 않고 외부 진단 도구로 취급한다.

## 먼저 적용할 공통 규칙

1. 루트 `AGENTS.md`와 [지식 베이스 안내](../index.md)를 먼저 읽는다.
2. 제품·API·데이터·아키텍처·보안·하네스 의미가 바뀌면
   [변경 영향 지도](change-impact-map.md)를 먼저 확인한다.
3. `.env`, credential·secret·인증서·개인 키의 내용은 읽거나 출력하지 않는다.
4. 커밋·브랜치·push·Pull Request·외부 이슈 작성은 사용자가 명시적으로 요청한
   경우에만 실행한다.
5. GitHub 연동 user-invoked 스킬은 정확한 스킬명·목적·읽기·쓰기 범위를 제시하고
   `~ 스킬을 호출할까요?`라고 물은 뒤 명시적인 호출 승인을 받아 시작한다.
   호출 승인은 읽기·분류·초안 작성만 허용하며 외부 쓰기 승인을 대신하지 않는다.
6. 삭제·외부 쓰기처럼 되돌리기 어려운 작업은 대상과 복구 방법을 보여주고 실행
   직전에 확인한다.
7. 작은 문서·설정 변경은 스킬을 과도하게 연결하지 말고 AGENTS 규칙과 관련 검증만
   적용한다.

## 빠른 선택

| 하려는 일 | 첫 선택 | 다음 단계 |
|---|---|---|
| 어떤 스킬을 써야 할지 모름 | `ask-matt` | 상황에 맞는 흐름으로 이동 |
| 요구사항·의사결정이 모호함 | `grill` | 요청 의도에 따라 1문 1답·batch·문서화 경로를 선택한 뒤 일반 구현이면 `plan`, 큰 작업이면 `wayfinder` |
| 큰 기능·구조·API·데이터·보안 결정 | `wayfinder` 호출 제안 | 호출 승인 → 결정 지도 → 각 GitHub 변경 승인 → `to-spec` 호출 제안 |
| 승인된 일반 구현 | `plan` | 승인 후 `implement` → `code-review` |
| 어려운 버그·성능 회귀 | `diagnosing-bugs` | `.dev` 진단 보고서에서 종료; 수정은 별도 요청 |
| 테스트부터 구현 | `tdd` | Java/JUnit·Gradle의 실제 seam에서 red → green |
| 현재 변경의 정합성 검토 | `change-impact-review` | 관련 정본·코드·테스트 재검사 |
| 하네스 완성도 평가 | `harness-audit` | 읽기 전용 평가와 개선 우선순위 |
| 새 GitHub 이슈 접수·분류 | `triage` 호출 제안 | 호출 승인 → 분류안 검토 → 승인된 댓글·label·상태만 반영 |
| 작업 결과를 검토 | `code-review` | Standards와 Spec을 별도로 확인 |
| 방금 한 작업을 배우고 싶음 | `teach-me` | 한 단계씩 이해 확인 → 종료 전 지속 학습 기록 판단 |
| 세션을 다른 에이전트로 넘김 | `handoff` | 새 세션에서 handoff 문서 참조 |

## 스킬 카탈로그

상태는 이 프로젝트에서의 기본 사용 위치를 뜻한다.

- **핵심**: 일반 작업 흐름에서 우선 튜닝·사용할 스킬
- **지원**: 특정 작업에서만 호출하는 스킬
- **조건부**: 명확한 상황에서만 호출하는 스킬
- **선택**: 개인 생산성·교육·실험 목적의 스킬. 기본 라우팅에는 넣지 않는다.

### 작업 흐름과 정합성

| 스킬 | 역할 | 언제 사용하는가 | 상태 |
|---|---|---|---|
| `ask-matt` | 적합한 흐름과 다음 승인 관문만 안내하고 실행하지 않는 라우터 | 사용할 스킬을 모르거나 작업 흐름을 선택할 때 | 핵심 |
| `grill` | 1문 1답·batch와 기록 없음·기록 필요를 조합하는 단일 진입점 | 사용자 또는 다른 스킬이 요구사항·아이디어·계획·결정을 공유 이해까지 구체화할 때 | 핵심·프로젝트 전용 |
| `plan` | 일반 구현 요구사항을 비정본 `.dev` 구현 체크리스트로 변환 | 구현 파일·순서·검증 범위를 확정할 때 | 핵심·프로젝트 전용 |
| `implement` | 승인된 plan·implementation ticket을 구현하고 연결된 spec을 맥락으로 읽음 | 특정 승인 실행 단위의 실제 코드나 동작을 변경할 때 | 핵심·프로젝트 전용 |
| `code-review` | 변경을 Standards와 Spec 두 축으로 검토 | 구현 후, 브랜치 또는 PR을 기준점과 비교할 때 | 핵심 |
| `change-impact-review` | 정본·문서·코드·테스트·스킬의 변경 영향과 불일치 검사 | 의미 있는 제품·구조·API·데이터·보안·하네스 변경 후 | 핵심·프로젝트 전용 |

### 개발·설계·검증

| 스킬 | 역할 | 언제 사용하는가 | 상태 |
|---|---|---|---|
| `codebase-design` | deep module, interface, seam, adapter, locality 어휘로 구조를 설계 | 모듈 인터페이스·테스트 seam·구조 개선을 논의할 때 | 핵심 |
| `domain-modeling` | 도메인 용어·경계·시나리오를 정리하고 필요한 결정을 기록 | 용어가 모호하거나 도메인 모델이 바뀔 때 | 핵심 |
| `diagnosing-bugs` | red feedback loop로 원인을 검증하고 `.dev` 진단 보고서를 남김 | 어려운 버그·간헐 오류·성능 회귀를 조사할 때 | 핵심 |
| `improve-codebase-architecture` | 구조적 마찰과 deepening 후보를 찾아 HTML 보고서로 제시 | 사용자가 아키텍처 개선 탐색을 요청할 때 | 조건부 |
| `tdd` | public interface에서 red → green → refactor 사이클을 안내 | 테스트 우선 구현이나 통합 테스트가 필요한 기능·수정 | 핵심 |
| `prototype` | 특정 로직·상태 모델·UI 질문에 답하는 폐기용 산출물 작성 | 문서만으로 결정하기 어려운 동작을 빠르게 확인할 때 | 조건부·튜닝 필요 |
| `research` | 1차 자료를 백그라운드로 조사하고 인용된 Markdown 결과를 남김 | 공식 문서·API·사양 사실을 확인해야 할 때 | 지원 |

### 이슈·GitHub·외부 작업

아래의 `setup-skills`, `gh-create-issue-from-template`, `gh-create-project-pr`, `triage`,
`to-spec`, `to-tickets`, `wayfinder`는 모두 user-invoked다. 라우터와 에이전트는 정확한
스킬명·목적·예상 읽기·쓰기 범위를 제시하고 호출할지 물을 수만 있으며, 사용자가
승인하기 전에는 시작하지 않는다. 호출 승인은 읽기·분류·초안 작성을 허용하고,
GitHub에 실제 반영하는 최종 묶음은 별도로 승인받는다.

| 스킬 | 역할 | 언제 사용하는가 | 상태 |
|---|---|---|---|
| `setup-skills` | issue tracker·triage label·domain docs 설정을 한 번 구성 | 관련 설정이 없거나 tracker를 바꿀 때 | 조건부 |
| `gh-create-issue-from-template` | 저장소의 현재 이슈 템플릿으로 GitHub 이슈 생성 | 이슈·버그·기능·작업 등록을 명시적으로 요청할 때 | 지원·프로젝트 전용 |
| `gh-create-project-pr` | 프로젝트 PR 규칙으로 Draft PR을 생성하고 CI·JaCoCo 확인 | 사용자가 PR 게시를 명시적으로 요청할 때 | 조건부·프로젝트 전용 |
| `triage` | GitHub 이슈·외부 PR의 category/state 분류안을 작성하고 승인된 변경만 반영 | 새 이슈를 검토하거나 `ready-for-agent` 변경안을 만들 때 | 지원 |
| `to-spec` | 확정된 대화·결정을 ready label 없는 parent GitHub spec issue 하나로 합성 | 큰 작업의 결정 지도가 정리됐고 구현 목표를 게시할 때 | 지원 |
| `to-tickets` | 승인된 spec을 blocker 관계가 있는 ready 구현 ticket으로 분할 | 구현을 여러 agent/session에 나눌 때 | 조건부 |
| `wayfinder` | 기본적으로 큰 작업의 장기 의사결정 map을 운영하고 `Notes`가 명시한 실행 task만 예외적으로 수행 | 한 세션에 담기 어려운 모호한 대규모 작업 | 조건부 |
| `git-guardrails-claude-code` | Claude Code의 위험한 Git 명령을 hook으로 차단 | push·reset·clean·branch 삭제 차단을 명시적으로 요청할 때 | 조건부·Claude 전용 |

### 세션·워크플로우·개인 생산성

| 스킬 | 역할 | 언제 사용하는가 | 상태 |
|---|---|---|---|
| `handoff` | 현재 대화를 임시 handoff 문서로 압축 | 새 세션에서 현재 맥락을 이어야 할 때 | 지원 |
| `claude-handoff` | handoff 요약을 Claude 백그라운드 세션으로 전달 | Claude CLI를 사용해 별도 agent에게 넘길 때 | 조건부·Claude 전용 |
| `loop-me` | 반복 업무를 `workflows/*.md` workflow spec으로 정리 | 반복 가능한 개인·팀 작업을 자동화 대상으로 정의할 때 | 선택 |
| `to-questionnaire` | 다른 사람의 지식을 받기 위한 비동기 질문지 생성 | 사용자가 혼자 결정할 수 없는 사실을 수집할 때 | 선택 |
| `teach` | 여러 세션에 걸친 학습 workspace와 lesson을 관리 | 장기 학습을 별도 workspace로 운영할 때 | 선택 |
| `teach-me` | 방금 수행한 작업이나 개념을 단계적으로 가르치고 종료 전 기록 필요성을 판단 | 사용자가 원리와 이유를 이해하고 다음 세션에서도 이어가고 싶을 때 | 선택·프로젝트 전용 |
| `writing-great-skills` | skill의 trigger·정보 계층·progressive disclosure를 검토 | 기존 skill을 만들거나 튜닝할 때 | 지원 |

`teach-me`는 완료·요약·중단·일시정지·주제 전환 전에 기록 후보 또는 생략 이유를
표시한다. 개인 학습 기록의 기본 경로는 Git에서 제외된 `.dev/learning/teach-me.md`이며,
정확한 경로와 최종 내용을 보여주고 사용자가 승인한 뒤에만 파일을 생성·수정한다.

## 프로젝트에서의 표준 흐름

### 작은 변경

문서·단순 설정·국소적인 코드 변경은 `AGENTS.md`를 읽고 직접 변경한다. 관련
검증만 실행하며 `wayfinder`, `to-spec`, `to-tickets`를 추가하지 않는다.

### 일반 구현

`/grill`로 미해결 요구사항을 정리한 뒤 `plan`을 작성하고 승인받는다. 이후
`implement`가 구현하고 `code-review`와 필요한 영향 검사를 수행한다.

### 큰 작업

`wayfinder` 호출을 제안해 승인받은 뒤 결정 지도와 각 tracker 변경 묶음을 승인받아
해결한다. 지도는 기본적으로 spec·계획·구현 ticket·코드를 만들지 않으며, `Notes`가 이름과
범위를 명시한 실행 task만 예외로 수행한다. 이어서 `to-spec`이 ready label 없는 parent spec
하나를, `to-tickets`가 ready 구현 tickets를 각각 별도 호출·쓰기 승인 뒤 게시하고, 승인된
ticket을 `implement`로 실행한다. Parent spec은 구현 맥락이며 직접 실행 입력이 아니다. 단,
승인된 `Notes`가 destination을 실행·검증까지 완료했다면 중복 spec·ticket을 만들지 않는다.
API·데이터·보안·아키텍처·하네스 의미가 바뀌면 `change-impact-review`까지 완료해야
한다.

### 버그 수정

`diagnosing-bugs`로 사용자의 실제 증상에 red-capable feedback loop를 만들고 원인·근거·권장
수정안을 `.dev` 진단 보고서에 남긴 뒤 종료한다. 사용자가 보고서를 검토하고 수정을 별도로
요청하면 그 요청을 작업 크기에 맞는 승인 경로로 보내며, 필요한 구현은 `tdd`로 진행한다.

### 하네스 변경

`docs/harness/completion-criteria.md`와 `change-impact-map.md`를 먼저 읽는다.
변경 후 관련 skill·검증기·문서 링크·회귀 테스트를 확인하고, 필요하면
`change-impact-review`를 실행한다. `.claude` 동기화는 `.agents` 튜닝이 끝난 뒤 수행한다.

## 튜닝 우선순위

1. **핵심 라우팅**: `ask-matt`, `grill`, `plan`, `implement`,
   `code-review`, `change-impact-review`를 Java 21·Spring Boot·PostgreSQL·Gradle·
   Windows PowerShell과 AGENTS 승인 규칙에 맞춘다.
2. **작업 추적**: `to-spec`, `to-tickets`, `triage`, `gh-create-*`, `wayfinder`가
   현재 GitHub 설정과 외부 쓰기 승인 규칙을 사용하도록 맞춘다.
3. **도구 가정**: `prototype`의 bash·TypeScript·커밋 가정을 프로젝트 환경에 맞게
   조정한다.
4. **선택 스킬**: `handoff`, `claude-handoff`, `loop-me`, `teach`, `teach-me`,
   `to-questionnaire`, `writing-great-skills`는 실제 사용 사례가 생길 때만 튜닝한다.

## 정합성·검증

스킬 내용의 정본은 `.agents/skills/<skill>/SKILL.md`다. `.claude/skills/<skill>/`는
같은 이름의 `.agents` 원본을 가리켜야 한다. 다음 검증을 사용한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\harness\verify-harness.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\harness\harness-doctor.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\harness\tests\verify-harness.tests.ps1
```

문서만 변경하는 경우 Gradle 전체 테스트·빌드는 기본 검증 범위에 포함하지 않는다.
제품 코드·빌드 설정·실행 경로를 함께 변경한 경우에만 README의 관련 Gradle 검증을
추가한다.
