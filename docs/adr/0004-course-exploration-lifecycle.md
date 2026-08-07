---
status: accepted
decision-date: 확인 불가
recorded-date: 2026-07-31
---

# ADR-0004 Course와 Exploration의 생명주기를 분리한다

코스는 AI 생성 뒤 수정·확정되는 여행 계획이고, 탐험은 확정 코스에 사용자가 합류해
시작·진행·완료하는 실행 단위다. 두 대상은 생성 시점과 변경 규칙이 다르다.

### 배경

코스는 AI 생성 뒤 수정·확정되는 여행 계획이고, 탐험은 확정 코스에 사용자가 합류해
시작·진행·완료하는 실행 단위다. 두 대상은 생성 시점과 변경 규칙이 다르다.

### 결정

- `Course`와 `Exploration`을 별도 Aggregate로 둔다.
- AI 생성 성공 시 `Course(DRAFT)`를 저장하고, 코스 확정 시
  `Exploration(BEFORE)`과 생성자 Participant를 만든다.
- 팀 역할, 참여 상태, 표시 이름과 위치 공유 동의는 별도 Team 쓰기 모델이 아니라
  `ExplorationParticipant`가 소유한다.
- Course 하나에는 전체 생명주기에서 Exploration을 최대 하나만 둔다.

### 검토한 대안

- Course와 Exploration을 하나의 생명주기로 관리하는 방식은 사용하지 않는다.
- 별도 Team Aggregate를 유지하는 방식의 당시 비교 근거는 기록되지 않았다.

### 영향

- 코스 확정과 탐험 시작은 서로 다른 상태 전환이다.
- 다른 참여자가 합류하기 전에는 확정을 취소하고 빈 Exploration을 정리할 수 있다.
- 참여 이탈은 Participant 상태를 바꾸며 기존 방문 기록을 삭제하지 않는다.

### 관련 문서

- [코스 설계 기능 명세](../product/features/course-design.md)
- [탐험 기능 명세](../product/features/exploration.md)
- [백엔드 아키텍처](../architecture/backend.md)

### 영향 대상

이번 마이그레이션에서는 결정 의미를 변경하지 않고 ADR 정본의 저장 위치와 파일 형식만
변경한다.

- 변경 유형: `architecture`, `harness`
- 수정이 필요한 문서·스킬·코드·테스트: ADR 경로를 가리키는 저장소 문서·스킬; 코드·테스트 없음
- 확인했지만 변경하지 않은 대상: 관련 제품 문서·백엔드 도메인 코드·테스트
- 미해결 항목: 저장소 밖의 과거 링크와 상위 #9의 후속 하네스 검증

### 변경 영향 검사

- 검사 스킬: `change-impact-review`
- 검사 결과: `통과`
- 검사 근거: 2026-08-03 17:59:06 +09:00 기준 원문 대조, 활성 참조 검색, 링크·구조 검사, `git diff --check`
