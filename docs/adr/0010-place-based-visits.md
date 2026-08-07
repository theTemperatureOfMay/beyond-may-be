---
status: accepted
decision-date: 2026-08-07
recorded-date: 2026-08-07
---

# ADR-0010 Visit의 방문 대상을 Place로 일반화하고 팀 상태를 파생한다

`Visit`은 Participant가 Exploration 중 인증한 `Place` 방문의 정본이다. 코스 장소
방문은 선택적 `CoursePlace` 문맥을 함께 가지며 코스 미포함 주변 장소 방문은 이 값이
없다. 동일 Participant의 같은 Place 인증은 Exploration 안에서 한 번만 허용하고,
팀 코스 완료율은 CoursePlace 문맥이 있는 Visit만 계산한다. 최초 Visit으로 해당
CoursePlace가 팀 완료된 뒤에도 다른 참여자는 같은 Place의 개인 Visit을 남길 수 있다.
팀 방문 기록과 팀 누적 밝힌 지도는 해당 Exploration의 모든 Participant Visit을 합쳐
계산하므로 코스 미포함 주변 장소 방문도 포함한다. 모든 CoursePlace가 팀 완료되면
Exploration을 자동 완료하고, OWNER는 그 전에 조기 완료할 수 있다.

이 결정은 Visit 대상을 CoursePlace로 제한하던
[ADR-0005](0005-visit-travel-record-source.md)를 대체한다.

### 영향 대상

- 수정: 백엔드 아키텍처, 백엔드 MVP 상태, 제품 논의 필요, Visit Entity,
  `V2__place_based_visits.sql`, Visit 도메인·마이그레이션 테스트
- 확인했지만 변경하지 않음: 탐험·여행 기록 기능 명세, 사용자 흐름,
  VisitPhoto·Exploration 코드
- 후속 구현: Visit Repository·Service·API, 팀 집계·완료 전환과 관련 테스트
- 미해결: 방문 인증과 완료·밝힌 지도 API 상세 계약

### 변경 영향 검사

- 검사: `change-impact-review`
- 결과: Entity·스키마 정합성 반영, API·집계 후속 구현 필요
- 근거: 관련 제품 문서·백엔드 아키텍처·ADR-0005·Visit/Exploration 코드 대조,
  Visit 도메인 테스트와 V1→V2 PostgreSQL 마이그레이션 테스트 통과
