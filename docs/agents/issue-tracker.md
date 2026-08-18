# Issue tracker: Local Markdown

여러 세션 작업의 생명주기 산출물은 `.dev/initiatives/`에 둔다. 이 파일들은 Git에서 무시되는 개인
작업 기록이며 프로젝트 정본이 아니다. 지속해야 하는 결론은 관련 정본 문서나 ADR로 옮긴다.

## Initiative 이름과 구조

- 디렉터리: `.dev/initiatives/yymmdd-nn-<initiative-slug>/`
- 날짜는 Asia/Seoul 기준이고 `nn`은 같은 날짜의 기존 initiative 다음 번호다.
- 기존 wayfinder 산출물이 있으면 `to-spec`과 `to-tickets`는 같은 initiative 디렉터리를 쓴다.
- wayfinder 없이 `to-spec`부터 시작하면 다음 initiative 디렉터리를 만든다.

```text
.dev/initiatives/
└── yymmdd-nn-<initiative-slug>/
    ├── map.md
    ├── decisions/
    │   ├── 01-<decision>.md
    │   └── 02-<decision>.md
    ├── spec.md
    └── tickets/
        ├── 01-<implementation>.md
        └── 02-<implementation>.md
```

`map.md`와 `decisions/`는 wayfinder를 쓸 때만 생긴다. `spec.md`는 initiative당 정확히
하나이고 decision과 implementation ticket은 파일 하나가 항목 하나를 소유한다. 번호는
blocker가 먼저 오도록 두 자리 순번으로 정한다.

## 공통 필드와 기록

- decision과 implementation ticket의 상태는 문서 제목 바로 아래의 `Status: <value>` 한 줄로 기록한다.
- blocker는 `Blocked by: NN, NN` 또는 `Blocked by: None`으로 기록한다.
- blocker는 같은 `decisions/` 또는 `tickets/` 디렉터리의 번호를 참조한다.
- 참조된 모든 문서가 `Status: resolved`일 때만 blocker가 해소된다.
- 이력은 기존 내용을 덮어쓰지 않고 `## Comments` 아래에 시간순으로 추가한다.
- 이 문서에서 `publish`는 구성된 로컬 파일을 생성하거나 갱신하는 것, `fetch`는 대상 파일과
  필요한 parent·blocker 파일을 읽는 것을 뜻한다.
- 경로 또는 필수 필드 구성이 없으면 중단한다. GitHub나 다른 로컬 경로로 대체하지 않는다.

`wayfinder`, `to-spec`, `to-tickets`, `triage`는 변경 직전에 정확한 파일 경로와 최종
내용을 보여 주고 그 묶음의 명시적 승인을 받은 뒤에만 파일을 바꾼다. 현재 frontier ticket의
구현 요청은 성공적인 검증 뒤 그 ticket을 `resolved`로 바꾸는 것까지 승인된 실행 입력에 포함한다.

## Wayfinder decision

- 파일: `decisions/NN-<decision-slug>.md`
- `Type`: `research`, `prototype`, `grilling`, `task`
- `Status`: `open`, `claimed`, `resolved`
- frontier: `Status: open`이고 blocker가 모두 해결됐으며 claimed되지 않은 decision 가운데
  번호가 가장 낮은 항목
- claim: 선택한 파일을 `Status: claimed`로 변경
- resolve: `## Answer`를 기록하고 `Status: resolved`로 변경한 뒤 `map.md`의
  `## Decisions so far`에 이름·상대 링크·한 줄 요약을 추가
- out of scope로 해결한 decision은 대신 `## Out of scope`에 링크하고 의존 decision을 같은
  묶음에서 정리한다.

## Spec

- 파일: initiative 루트의 `spec.md`
- 입력: 해결된 대화 또는 모든 필요한 decision이 해결된 `map.md`
- 역할: 여러 세션 작업의 구현 목표와 결정 맥락. 직접 구현 단위나 프로젝트 정본은 아니다.
- `to-spec`은 이 파일 하나만 만들거나 갱신한다.

## Implementation ticket

- 파일: `tickets/NN-<implementation-slug>.md`
- parent: `Parent: ../spec.md`
- `Status`: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`,
  `resolved`
- frontier: `Status: ready-for-agent`이고 blocker가 모두 해결된 ticket 가운데 번호가 가장
  낮은 항목
- `to-tickets`는 ticket마다 파일 하나를 만들고 `Blocked by`에 번호를 기록한다.
- `triage`는 모든 sibling ticket을 읽어 frontier를 계산하고 대상이 frontier일 때만
  `implement`를 추천한다. `Status`와 `## Comments`만 승인된 내용으로 갱신한다.
- `implement`는 전체 검증이 성공했을 때만 실행한 ticket을 `Status: resolved`로 갱신한다.
  실패하거나 미실행 검사가 있으면 상태를 바꾸지 않는다.
