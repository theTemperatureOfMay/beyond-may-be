---
status: accepted
decision-date: 2026-08-23
recorded-date: 2026-08-23
---

# ADR-0019 장소 상세 조회는 비어 있는 정보를 동기 보강해 같은 응답에 포함한다

추천 응답 뒤 비동기 보강만 사용하면 아직 작업이 끝나지 않았거나 작업이 유실된 장소의
상세 화면이 실제 외부 정보가 있어도 빈 값으로 보일 수 있다. 공개 장소 상세 조회는
PostgreSQL `places`를 먼저 읽되 설명이나 운영시간이 비어 있을 때만 TourAPI를 동기
호출하고, 보강 결과를 저장한 뒤 같은 요청에 반환한다.

### 결정

- 인증 사용자는 `GET /api/v1/places/{placeId}`로 활성 장소의 카탈로그 상세를 조회한다.
  방문 여부와 버튼 활성 상태는 이 API에 포함하지 않고 탐험·방문 API와 클라이언트 GPS
  상태를 조합한다.
- 저장된 설명과 운영시간은 그대로 사용하며 외부 값으로 덮어쓰지 않는다. 설명이 비어
  있으면 `detailCommon2.overview`, 운영시간이 비어 있으면 `detailIntro2`의 콘텐츠 유형별
  운영시간 필드만 조회한다.
- TourAPI가 정상 응답했지만 값이 없으면 설명에는 `상세 설명 정보 없음`, 운영시간에는
  `운영시간 정보 없음`을 저장하고 같은 응답에 반환한다. 콘텐츠 ID나 콘텐츠 유형 ID가
  없어 조회할 수 없는 필드에도 같은 문구를 저장한다. 이 문자열이 재호출 방지 표식이며
  별도 상태 열이나 migration은 추가하지 않는다.
- 연결 실패, timeout, HTTP·TourAPI 오류, 파싱 불가 응답이나 필요한 서비스 키 부재는
  값을 저장하지 않고 `503 Service Unavailable`·`PLACE503`을 반환한다. 따라서 다음 상세
  요청은 비어 있는 필드만 다시 시도한다.
- 장소가 없거나 비활성이면 `404 Not Found`·`PLACE404`, DB 조회·저장과 처리되지 않은
  내부 실패는 기존 `500 Internal Server Error`·`COMMON500`을 사용한다.
- 추천 응답 뒤 비동기 보강은 사전 채우기 용도로 유지한다. 외부 실패는 추천 응답을
  변경하지 않으며, 상세 GET만 외부 오류를 공개 오류로 변환한다.
- 동시에 시작한 첫 요청과 비동기·동기 작업이 외부 호출을 중복할 수 있음을 허용한다.
  빈 필드 조건부 갱신으로 기존 값은 보호하며 lock, single-flight와 분산 작업 상태는
  도입하지 않는다.
- TourAPI `overview`는 일반 장소 설명으로만 사용한다. 5·18 연관 의미는 서버나 AI가
  생성하지 않고 기존 `description`에 사람이 검수해 저장한 문구만 유지한다.
- `thumbnailUrl`은 저장값 또는 null을 반환하고 이번 보강 대상에 포함하지 않는다. 좌표는
  `BigDecimal`과 `numeric(9,6)` 정밀도를 유지한다.

이 결정은 공개 상세 조회가 TourAPI를 직접 호출하지 않던 경계와 비동기·동기 공통 보강이
정상 정보 부재 또는 조회 식별자 부재를 안내 문구로 저장하는 정책에서
[ADR-0016](0016-asynchronous-tourapi-place-detail-enrichment.md)을 부분 대체한다. 추천 응답과
비동기 보강을 분리하고 `places`를 런타임 정본으로 유지하는 결정은 계속 적용한다.

### 검토한 대안

- 비동기 보강만 유지하면 상세정보가 존재해도 첫 상세 응답이 빈 값일 수 있어 사용하지
  않는다.
- 빈 값을 그대로 반환하고 프런트엔드에서만 안내하면 매 상세 요청이 외부 조회를 반복할
  수 있어 사용하지 않는다.
- 별도 확인 상태 열이나 보강 작업 테이블은 두 문자열로 충분히 구분되는 현재 계약에
  migration과 상태 동기화를 추가하므로 사용하지 않는다.
- 첫 호출 중복 방지를 위한 lock이나 single-flight는 실제 외부 호출량 문제가 확인될 때
  검토한다.

### 영향 대상

- 변경 유형: `product`, `api`, `data`, `architecture`, `security`
- 변경한 대상: 장소 상세 Controller·DTO·Converter·Service, 활성 장소 Repository 조회,
  TourAPI 상세 클라이언트 오류 구분, 동기·비동기 보강 Service, 공통 오류 코드, 관련
  제품·아키텍처 정본, Postman과 테스트
- 확인했지만 변경하지 않음: `places` Entity와 V1~V9 schema, 인증 필터·보안 설정,
  추천 API 계약, 방문 인증 API, 썸네일 저장 방식, TourAPI timeout 설정
- 확인하지 못함: 실제 TourAPI 운영 응답, 운영 서비스 키와 ECS·RDS 상태
- 미해결: 외부 장소 이미지의 저장·프록시·직접 링크 및 저작권 정책은 제품 논의 필요에
  남긴다.
- 수정이 필요한 스킬·하네스: 없음

### 변경 영향 검사

- 검사 스킬: `change-impact-review`
- 검사 일자: 2026-08-23
- 검사 결과: `통과`
- 검사 근거: Controller·DTO·Service·Converter·Repository·TourAPI 클라이언트와
  SecurityConfig, V5·V6 schema, ADR-0016, 제품 기능·사용자 흐름·MVP·백엔드 아키텍처,
  Postman·ClueDoc·테스트를 대조했다. 장소 패키지 전체 테스트, Spotless, 테스트 제외 전체
  빌드, 하네스 semantic 검사, Postman JSON, 문서 링크와 `git diff --check`가 통과했다.
  전체 테스트는 Docker 데몬 무응답으로 완료하지 못했으며 실제 TourAPI·운영 ECS·RDS와
  비밀값은 확인하지 않았다.

### 관련 문서

- [장소 선택 기능 명세](../product/features/place-selection.md)
- [탐험 기능 명세](../product/features/exploration.md)
- [전체 기능 명세](../product/feature-spec.md)
- [백엔드 MVP 상태](../product/mvp.md)
- [백엔드 아키텍처](../architecture/backend.md)
- [ADR-0016](0016-asynchronous-tourapi-place-detail-enrichment.md)
