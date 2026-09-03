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

- 변경 유형: `product`, `api`, `data`, `architecture`, `security`
- 변경한 대상: 백엔드 아키텍처·MVP 상태·여행 기록 기능 명세·제품 논의 문서, Visit
  Entity·Repository·Service·Controller·DTO·converter와 관련 테스트,
  `V2__place_based_visits.sql`, Postman Collection
- 확인했지만 변경하지 않음: 사용자 흐름, VisitPhoto·Exploration Entity, 기존 Flyway
  migration, 실시간 위치·방문·상태 채널
- 확인하지 못함: 프런트엔드 지도 렌더링·이미지 저장과 공유, 운영 데이터의 실제 집계
- 후속 구현 완료: 방문 인증과 팀 방문 기록·밝힌 지도 집계, 자동·OWNER 조기 완료 전환
- 미해결: 없음. 방문 인증·완료·팀 방문 기록·밝힌 지도 API 상세 계약을 후속 구현에서
  확정했다.

### 변경 영향 검사

- 검사: `change-impact-review`
- 검사 일자: 2026-09-03
- 결과: `통과 (finding 없음)`
- 근거: Place 기반 Visit 정본과 현재·과거 참여자 인가, 코스·주변 장소 집계, Place 좌표만
  반환하는 밝힌 지도 API를 제품 명세·MVP 상태·백엔드 아키텍처·ADR-0005·0025·0027,
  코드·테스트·Postman과 대조했다. 전체 423개 테스트, `spotlessCheck`, 전체 `build`,
  Postman Collection과 saved JSON body 102개 파싱, 제품 지식·하네스 semantic 검사와
  `git diff --check`가 통과했고 Standards·Spec 검토 finding은 없었다.
