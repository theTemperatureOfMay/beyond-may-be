# Beyond May Be 상세 기능 명세

이 문서는 전체 서비스가 목표로 하는 상세 기능 정본의 대표 진입점이다. 기능별 화면
동작과 예외는 아래 영역 문서에서 관리하며, 현재 백엔드 구현 상태는
[백엔드 MVP 상태](mvp.md)에서 별도로 관리한다.

## 정본 규칙

- 이 문서와 연결된 상세 문서는 변경 가능한 최신 기준이며 기능 변경과 같은
  Pull Request에서 갱신한다.
- 제품 행동과 기능 요구사항은 이 명세와 연결된 기능별 상세 문서를 최우선으로 한다.
  이전 사용자 흐름·설계 문서·코드와 충돌하면 기능명세를 기준으로 영향 대상을 갱신한다.
- `[결정 필요]`, `[백엔드 확인]`, `[디자인 필요]`는 확정값이 아니다.
- 상세 동작을 구현하기 전에 [논의 필요](open-questions.md)도 함께 확인한다.
- 소기능 ID는 문서가 나뉘어도 유지하는 안정적인 참조 키다.

## 기능 영역

| 영역 | ID 범위 | 소기능 수 | 상세 문서 |
|---|---|---:|---|
| 초기 진입 및 성향 검사 | `1.*` | 7 | [온보딩·성향·세션](features/onboarding-preference.md) |
| 장소 선택 | `2.*` | 9 | [기간·추천·스와이프·선택](features/place-selection.md) |
| 코스 설계 | `3.*` | 8 | [AI 코스·수정·확정·공유](features/course-design.md) |
| 팀 탐험 지도 | `4.*` | 11 | [팀 합류·탐험·방문·주변 장소](features/exploration.md) |
| 팀 내 여행 기록 | `5.*` | 4 | [진행·완료 코스와 방문 기록](features/travel-records.md) |
| 공통·예외 처리 | `6.*` | 10 | [오류·재시도·빈 상태·공통 UI](features/common-policies.md) |

총 49개 소기능의 제목과 상세 동작은 각 영역 문서에 정확히 한 번씩 존재한다.

## 한국관광공사 OpenAPI 적용

| 적용 기능 | 호출 시점 | 호출 OpenAPI | 용도 |
|---|---|---|---|
| 관광 장소·분류 데이터 수집 | 장소 데이터 최초 수집 | `KorService2/ldongCode2`, `KorService2/lclsSystmCode2`, `KorService2/areaBasedList2`, `KorService2/detailCommon2`, `KorService2/detailIntro2` | 광주 장소와 TourAPI 신분류체계를 저장하고 여행 성향별 장소 풀 구성 |
| 중심관광지 순위 수집 | 중심관광지 순위 갱신 | `LocgoHubTarService1/areaBasedList1` | 추천 후보의 보조 순위 데이터 구성 |
| 2.1.2 AI 추천 장소 목록 조회 | 사용자 추천 요청 중 최초 유형별 할당량이 부족할 때 | `KorService2/areaBasedSyncList2` 최대 1회 | 유효한 광주 변경분을 DB에 먼저 저장하고 같은 요청 후보로 포함한 뒤 최대 20곳 구성 |
| 2.2.2 장소 스와이프 — 싫어요 | 추가 회차의 미노출 활성 후보가 20곳보다 적을 때 | `KorService2/areaBasedSyncList2` 최대 1회 | 유효한 신규 장소를 DB에 저장하고 다시 계산한 뒤, 그래도 부족하면 기존 싫어요 장소를 한 번만 재추천해 20곳 구성 |
| 2.1.2 AI 추천 장소 목록 조회 | 새 추천 저장 후 응답과 분리된 사후 보강 | `KorService2/detailCommon2`, `KorService2/detailIntro2` | 응답에 포함된 장소의 비어 있는 설명·운영시간만 DB에 최선 노력으로 보강 |
| 2.2.4 장소 상세 보기 | 사용자가 추천 장소 상세 열기 | 관광 OpenAPI 직접 호출 없음 | 검수·저장된 장소 상세 사용 |
| 4.4.1 주변 장소 추천 | 사용자가 주변 장소 더보기 클릭 | `KorService2/locationBasedList2` | 현재 GPS에서 1km 이내 관광 장소 후보 조회 |
| 4.4.2 탐험 지도 내 장소 상세 | 사용자가 주변 장소 상세 열기 | 관광 OpenAPI 직접 호출 없음 | 2.2.4와 같은 검수·저장 장소 상세 사용 |

추천 요청의 부족분 보충, 상세정보 사후 보강과 외부 실패 대체는
[장소 선택 기능 명세](features/place-selection.md)를 따른다. 같은 명세가 Groq
`openai/gpt-oss-20b`의 최대 60곳 후보 선별, strict ID 검증과 규칙 기반 전체 폴백
경계도 정의한다. 주기적 전체 갱신과 주변 장소 수집 정책은
[논의 필요](open-questions.md)에서 확정한다.

## 함께 확인할 문서

- [논의 필요](open-questions.md): 현재 기준이 없거나 원문 안에서 충돌하는 조건
- [백엔드 MVP 상태](mvp.md): 소기능별 백엔드 책임, 우선순위와 구현 근거
- [사용자 흐름](user-flow.md): 역할별 종단 흐름과 주요 분기
- [과거 자료](legacy/README.md): 정본이 아닌 이전 플로우차트 안내
