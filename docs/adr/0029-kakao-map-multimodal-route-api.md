---
status: accepted
decision-date: 2026-09-20
recorded-date: 2026-09-20
supersedes: ADR-0009
---

# ADR-0029 서버 통합 길찾기는 카카오맵 REST API를 사용한다

코스와 탐험 화면에서 출발지·도착지 사이의 도보 및 대중교통 경로를 제공하기 위해
백엔드가 카카오맵 REST API의 도보·대중교통 경로 조회 API를 호출한다. 지도·핀·뷰포트
렌더링은 계속 카카오맵 지도 SDK를 사용한다.

## 결정

- `GET /api/v1/routes`가 출발·도착 좌표를 받아 카카오맵 도보 API와 대중교통 API를 각각 호출한다.
- 도보 API의 `route`를 `walking`으로 반환하고, 대중교통 API의 첫 `routes` 항목을 `publicTransit`으로 반환한다.
- 대중교통 경로의 첫 탑승지 전·마지막 하차지 후 도보 구간이 없으면 도보 API로 각각 조회해
  `publicTransit.steps` 앞뒤에 `WALKING` 단계로 병합하고, 전체 거리·시간에 합산한다.
- 도보와 대중교통 경로는 각각 없을 수 있으며, 둘 다 없으면 `ROUTE404` 예외를 반환한다.
- 외부 길찾기 API 호출 실패는 `ROUTE503`으로 반환한다.
- 경로 계산 결과와 폴리라인은 저장하지 않는다.
- 20분 기준은 사용하지 않는다. 경로 유형의 존재 여부만으로 응답을 구성한다.

이 결정은 지도·경로 제공자 책임을 프런트엔드 Kakao Maps·TMAP으로 분리했던
[ADR-0009](0009-kakao-map-tmap-walking-route.md)를 대체한다. 검수된 장소를
PostgreSQL `places`에서 사용하는 원칙은 [ADR-0003](0003-verified-place-catalog.md)을
그대로 따른다.

## 영향 대상

- 수정: 경로 Controller·DTO·Service·Kakao client와 관련 테스트
- 수정: 백엔드 아키텍처, 코스 설계 기능 명세, 백엔드 MVP 상태, 제품 논의 필요
- 추가: `GET /api/v1/routes` Postman 요청과 saved example
- 확인했지만 변경하지 않음: 장소 정본, CoursePlace 저장 구조, 경로 결과 저장 구조
- 미해결: 프런트엔드에서 새 응답을 지도·경로 UI에 연결하는 작업과 실제 카카오맵 API 권한·쿼터 검증

## 변경 영향 검사

- 검사 스킬: `change-impact-review`
- 검사 일자: 2026-09-20
- 결과: `통과`
- 근거: 카카오맵 공식 REST API 문서의 `/v2/routing/walk`·`/v2/routing/publictraffic`
  계약과 Controller·Service·Client·테스트·Postman 예시를 대조했다. 관련 테스트,
  전체 테스트, `spotlessCheck`, 전체 `build`, `git diff --check`가 통과했다.
