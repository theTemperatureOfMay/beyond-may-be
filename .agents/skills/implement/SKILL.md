---
name: implement
description: 해결·승인된 한 세션 구현 요청 또는 frontier implementation ticket을 구현하고 필요한 TDD·검증을 수행한다. 연결된 spec은 맥락으로만 읽으며 spec만 지정된 요청에는 사용하지 않는다.
---

# 승인된 구현 입력 실행

## 1. 시작 조건

1. `AGENTS.md`와 지정된 대화 요구사항 또는 implementation ticket을 끝까지 읽는다.
2. 연결된 spec·설계·정본 문서와 ticket comments가 있으면 함께 읽는다. Spec은 실행 단위가
   아니라 구현 목표와 결정 맥락이다.
3. 대화 요구사항이면 구현 범위와 완료 조건이 해결됐고 사용자가 제시된 구현을 승인했는지
   확인한다.
4. Local Markdown ticket이면 구현 직전에 현재 본문, `## Comments`, 완료 조건, 모든 sibling
   ticket과 `Blocked by`가 가리키는 ticket의 `Status`를 다시 읽는다. 대상이 blocker가 모두
   해결된 최저 번호 `ready-for-agent` frontier가 아니면 구현하지 않고 현재 frontier를 보고한다.
5. 해결·승인된 한 세션 구현 요청은 즉시 실행 입력으로 사용한다.
6. 사용자가 특정 frontier implementation ticket의 구현을 명확히 요청하면 승인된 입력으로
   본다. 미해결 blocker가 있으면 구현하지 않고 현재 blocker를 보고한다.
7. spec만 지정되면 구현하지 않고 `/to-tickets`로 넘긴다. 읽기·분류·초안 작성은 자동으로
   시작할 수 있지만 local ticket 파일 쓰기는 최종 경로와 내용을 보여 주고 별도 승인받는다.
8. 승인된 실행 입력이 없으면 구현하지 않고 필요한 입력과 승인을 요청한다.

해결·승인된 한 세션 구현 요청과 현재 frontier implementation ticket만
실행 입력이다. 위험도는 이 입력 종류가 아니라 안전 확인과 검증 범위를 정한다.

## 2. 구현

- 테스트가 필요한 코드는 TDD 방식임을 알리고
  `실패하는 테스트 → 통과하는 최소 코드 → 코드 정리` 순서로 구현한다.
- 문서와 단순 설정은 TDD 제외 작업임을 알리고 필요한 검증만 수행한다.
- 승인된 범위 또는 ticket acceptance criteria 순서로 작업하고 완료 증거는 구현 기록에
  모은다.
- 하네스와 관계없는 사용자 변경은 수정하거나 되돌리지 않는다.
- 승인 범위의 작은 구현 차이는 처리하되 목적·구조·공개 계약·완료 조건이 달라지면
  중단하고 실행 입력 수정과 재승인을 요청한다.

## 3. 안전

- 데이터 삭제처럼 되돌리기 어려운 작업은 실행 직전에 다시 확인한다.
- 승인된 실행 입력에 포함된 Git으로 복구 가능한 코드 파일 삭제는 다시 묻지 않는다.
- 커밋, 브랜치, push, Pull Request와 GitHub comment, close, label, status 변경은 별도 요청과
  승인이 있을 때만 수행한다. 구현 요청이나 입력 승인을 Git·GitHub 쓰기 승인으로 재사용하지
  않는다.
- 비밀번호, API 키, 접근 토큰과 개인정보를 코드·문서·로그에 기록하지 않는다.

## 4. 검증

낮은 위험 작업은 관련 검사 또는 필요한 최소 회귀 검증과 diff 검토를 수행한다. 보통·높은
위험 작업은 다음 순서로 검증한다.

1. 전체 테스트
2. 코드 품질 검사
3. 전체 빌드
4. 변경 코드와 diff를 승인된 입력·사용자 요청에 대조

Windows Gradle 프로젝트의 기본 명령은 `.\gradlew.bat test`,
`.\gradlew.bat spotlessCheck`, `.\gradlew.bat build`다. 이번 변경 때문에 실패한
검사는 수정하고, 기존 실패와 환경 문제는 구분해 보고한다.

## 5. 완료 기록

Ticket 입력이면 `.dev/implementation/`에 구현 문서를 작성한다. Asia/Seoul 날짜와 그날의 다음 작업 번호로
`yymmdd-nn-{작업 내용}-implementation.md`를 만든다. 대화에서 시작한 한 세션 작업은 별도
구현 문서를 만들지 않는다. 기록을 만들 때는 입력 링크, 실제 변경 파일, 입력과의 차이,
테스트·품질·빌드·자체 검토 결과와 남은 문제를 기록한다.

Local Markdown ticket은 모든 검증이 성공한 경우에만 `Status: resolved`로 갱신하고, 실패하거나 미실행 검사가 있으면 해결
상태로 바꾸지 않는다. 연결된 `spec.md`는 수정하지 않는다. 중단되면 구현 기록에 완료한 항목,
`구현 실패`와 이유를 남긴다.
