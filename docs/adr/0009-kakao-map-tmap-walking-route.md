---
status: accepted
decision-date: 2026-08-07
recorded-date: 2026-08-07
---

# ADR-0009 Kakao Maps는 지도를, TMAP은 도보 경로를 담당한다

런타임 장소 정본은 검수된 PostgreSQL `places`이며 AI는 이 장소만 선별한다. 화면의
지도·핀·뷰포트 렌더링은 Kakao Maps API를 사용하고, 장소 사이의 도보 경로와
폴리라인 계산은 TMAP API를 사용한다. 백엔드는 계산된 경로를 저장하지 않는다.

이 결정은 Kakao Maps API가 지도와 경로 계산을 모두 담당하던
[ADR-0003](0003-verified-place-catalog.md)을 대체한다.

### 영향 대상

- 수정: 코스 설계 기능 명세, 백엔드 아키텍처, 백엔드 MVP 상태
- 후속 구현: 프런트엔드 TMAP 도보 경로 연동과 폴백 검증
- 확인했지만 변경하지 않음: 현재 백엔드 Controller·DTO·Service
- 미해결: TMAP 요청·응답과 오류 폴백의 상세 계약

### 변경 영향 검사

- 검사: `change-impact-review`
- 결과: 문서 정합성 통과, 프런트엔드 TMAP 연동 후속 구현 필요
- 근거: 관련 제품·아키텍처·ADR·코드 대조, 제품 지식 베이스 검증 통과,
  `git diff --check` 통과
