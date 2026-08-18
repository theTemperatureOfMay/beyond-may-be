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
[ADR-0003](0003-verified-place-catalog.md)의 지도·경로 제공자 책임에 관한 일부
결정만 대체한다. 검수된 장소 DB를 런타임 정본으로 사용하는 ADR-0003의 핵심 결정은
계속 유효하다.

### 영향 대상

- 관계 정정: ADR-0003의 장소 정본 결정은 계속 유효하고, 이 ADR은 지도·경로
  제공자 책임만 대체한다.
- 수정: 코스 설계 기능 명세, 백엔드 아키텍처, 백엔드 MVP 상태
- 후속 구현: 프런트엔드 TMAP 도보 경로 연동과 폴백 검증
- 확인했지만 변경하지 않음: 현재 백엔드 Controller·DTO·Service
- 미해결: TMAP 요청·응답과 오류 폴백의 상세 계약

### 변경 영향 검사

- 검사: `change-impact-review`
- 검사 일자: 2026-08-09
- 결과: 문서 정합성 `통과`, 프런트엔드 TMAP 연동 후속 구현 필요
- 근거: ADR-0003·ADR-0009, 관련 제품·아키텍처 문서와 코드 대조,
  제품 지식 베이스·하네스 semantic 검증과 `git diff --check` 통과
