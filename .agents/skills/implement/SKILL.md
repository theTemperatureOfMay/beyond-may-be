---
name: implement
description: 승인된 일반 구현 plan 또는 큰 작업의 implementation ticket을 구현하고 TDD·전체 검증·구현 기록을 연결한다. 연결된 spec은 구현 맥락으로만 읽는다. 작은 작업, 계획 작성, spec만 지정된 요청과 승인되지 않은 입력에는 사용하지 않는다.
---

# 승인된 구현 입력 실행

## 1. 시작 조건

1. `AGENTS.md`와 지정된 plan 또는 implementation ticket을 끝까지 읽는다.
2. 연결된 spec·설계·정본 문서와 ticket comments가 있으면 함께 읽는다. Spec은 실행 단위가
   아니라 구현 목표와 결정 맥락이다.
3. plan이면 상태가 `승인됨`인지 확인한다. GitHub ticket이면 구현 직전에 현재 body, comments와
   완료 조건을 다시 읽는다.
4. 사용자가 특정 plan 또는 ticket의 구현을 명확히 요청하면 승인된 입력으로 본다.
5. spec만 지정되면 구현하지 않고 `/to-tickets`의 예상 읽기·쓰기 범위를 설명한 뒤 호출 승인을
   요청한다.
6. 승인된 실행 입력이 없으면 구현하지 않고 필요한 입력과 승인을 요청한다.

승인된 일반 구현 plan과 큰 작업의 implementation ticket만 실행 입력이다. 작은 작업은 공통
하네스의 직접 구현 절차를 따르며 이 스킬을 사용하지 않는다.

## 2. 구현

- 테스트가 필요한 코드는 TDD 방식임을 알리고
  `실패하는 테스트 → 통과하는 최소 코드 → 코드 정리` 순서로 구현한다.
- 문서와 단순 설정은 TDD 제외 작업임을 알리고 필요한 검증만 수행한다.
- plan 체크박스 또는 ticket acceptance criteria 순서로 작업한다. GitHub issue를 진행 기록으로
  수정하지 말고 완료 증거는 구현 기록에 모은다.
- 하네스와 관계없는 사용자 변경은 수정하거나 되돌리지 않는다.
- 계획 범위의 작은 구현 차이는 처리하되 목적·구조·공개 계약·완료 조건이 달라지면
  중단하고 계획 수정과 재승인을 요청한다.

## 3. 안전

- 데이터 삭제처럼 되돌리기 어려운 작업은 실행 직전에 다시 확인한다.
- 승인된 계획에 포함된 Git으로 복구 가능한 코드 파일 삭제는 다시 묻지 않는다.
- 커밋, 브랜치, push, Pull Request와 GitHub comment, close, label, status 변경은 별도 요청과
  승인이 있을 때만 수행한다. 구현 요청이나 입력 승인을 Git·GitHub 쓰기 승인으로 재사용하지
  않는다.
- 비밀번호, API 키, 접근 토큰과 개인정보를 코드·문서·로그에 기록하지 않는다.

## 4. 검증

구현 후 다음 순서로 검증한다.

1. 전체 테스트
2. 코드 품질 검사
3. 전체 빌드
4. 변경 코드와 diff를 계획·사용자 요청에 대조

Windows Gradle 프로젝트의 기본 명령은 `gradlew.bat test`,
`gradlew.bat spotlessCheck`, `gradlew.bat build`다. 이번 변경 때문에 실패한
검사는 수정하고, 기존 실패와 환경 문제는 구분해 보고한다.

## 5. 완료 기록

`.dev/implementation/`에 구현 문서를 작성한다. Plan 입력이면 같은 작업 번호와 이름을
사용해 `-plan.md`를 `-implementation.md`로 바꾼다. Ticket 입력에 연결된 plan이 없으면
Asia/Seoul 날짜와 그날의 다음 작업 번호로
`yymmdd-nn-{작업 내용}-implementation.md`를 만든다. 입력 링크, 실제 변경 파일, 입력과의
차이, 테스트·품질·빌드·자체 검토 결과와 남은 문제를 기록한다.

Local plan은 완료하면 체크박스와 상태를 `구현 완료`로 갱신한다. 연결된 GitHub spec과 ticket에는
자동으로 comment하거나 close하지 않는다. 사용자가 GitHub 갱신을 요청하면 최종 comment, label과
state 변경 묶음을 보여 주고 별도 승인을 받은 뒤 반영한다. 중단되면 구현 기록에 완료한 항목,
`구현 실패`와 이유를 남긴다.
