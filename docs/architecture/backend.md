# Beyond May Be 백엔드 아키텍처

이 문서는 백엔드가 목표로 하는 도메인 책임, 영속 데이터 관계와 외부 시스템 경계를
정의하는 아키텍처 정본이다. 상세 화면 동작은 [기능 명세](../product/feature-spec.md),
현재 구현 상태는 [백엔드 MVP 상태](../product/mvp.md), 아직 확정되지 않은 조건은
[제품 논의 필요](../product/open-questions.md)를 따른다.

현재 코드가 이 문서와 다르면 이 문서를 목표 구조로 사용하되, 구현 완료 여부를
추정하지 않고 백엔드 MVP 상태와 실제 코드를 함께 확인한다.

## 설계 원칙

- 테이블 수가 아니라 데이터 소유권과 생명주기를 기준으로 Aggregate 경계를 나눈다.
- PostgreSQL은 재접속·공유·기록에 필요한 서버 정본을 보존한다.
- 현재 카드, 되돌리기와 일괄 전송 전 반응은 프런트엔드 로컬 스토리지에 둔다.
- `Course`와 `Exploration`은 생명주기가 다르므로 분리한다.
- 한국관광공사 OpenAPI는 초기 장소 수집에만 사용하고 런타임에는 `places`를 사용한다.
- Redis는 MVP 범위에서 사용하지 않는다.

## 도메인 책임

| 영역 | 책임 | 주요 영속 데이터 |
|---|---|---|
| 사용자·성향 | 간편 식별, 최종 여행 성향 | `users` |
| 성향 질문 | 21개 질문 풀, 선택지와 유형별 가중치 | `questions`, `question_options` |
| 장소·추천 | 검수된 장소 카탈로그, 사용자별 현재 추천 결과 | `places`, `recommendation_sets` |
| 코스 | AI 생성 결과, 방문 순서, 수정과 확정 | `courses`, `course_places` |
| 탐험 | 확정 코스의 팀 합류, 역할, 시작과 완료 | `explorations`, `exploration_participants` |
| 방문 기록 | 참여자별 방문 인증과 선택적 다중 사진 | `visits`, `visit_photos` |

## 인증과 권한

다음은 목표 백엔드 구조의 권한 경계다. 현재 구현 여부는
[백엔드 MVP 상태](../product/mvp.md)에서 확인하며, 사용자 식별 원칙은
[ADR-0007](../adr/0007-recommendation-set-user-ownership.md)을 따른다.

| 작업 | 권한 |
|---|---|
| 질문·성향 계산, 유효한 공유 코스 미리보기 | 비인증 허용 |
| 기간·추천·반응·장소 선택 | 인증된 사용자 |
| `DRAFT` 코스 수정·확정 | 해당 `Course` 소유자 |
| 공유 코스 합류 | 인증된 사용자, 활성 참여 없음, 유효한 공유 만료 시각 |
| 탐험 시작·팀원 조회·주변 장소 추천 | 해당 `Exploration`의 `ACTIVE Participant` |
| 방문 인증·사진 첨부 | 해당 `Exploration`의 `ACTIVE Participant` |
| 팀 방문 기록·팀 누적 밝힌 지도 조회 | 해당 `Exploration`의 현재 또는 과거 `Participant` |
| 탐험 조기 완료 | 해당 `Exploration`의 `OWNER Participant` |

인증된 요청의 사용자·참여자 식별자는 body나 query 값이 아니라 서버가 확인한
인증 컨텍스트와 참여 관계에서 결정한다. 구체적인 인증 토큰·세션 방식은 별도 인증
설계 범위다. 공유 만료, 참여 상태, 방문 소속과 공개 범위가 바뀌면 이 표를 제품
논의 필요 문서와 함께 갱신한다.

## 목표 논리 관계

```mermaid
erDiagram
    USERS ||--o| RECOMMENDATION_SETS : keeps_current
    QUESTIONS ||--|{ QUESTION_OPTIONS : contains

    USERS ||--o{ COURSES : creates
    COURSES ||--|{ COURSE_PLACES : contains
    PLACES ||--o{ COURSE_PLACES : referenced_by
    COURSES ||--o| EXPLORATIONS : activates
    EXPLORATIONS ||--|{ EXPLORATION_PARTICIPANTS : has
    USERS ||--o{ EXPLORATION_PARTICIPANTS : joins
    EXPLORATION_PARTICIPANTS o|--o| EXPLORATIONS : starts
    PLACES ||--o{ VISITS : visited_at
    COURSE_PLACES o|--o{ VISITS : course_context
    EXPLORATION_PARTICIPANTS ||--o{ VISITS : records
    VISITS ||--o{ VISIT_PHOTOS : has
```

## 테이블별 기준

### `users`

- `user_id`가 내부 식별자다.
- `(nickname, identification_code)`가 표시·간편 로그인 식별자이며 복합 유일하다.
- 같은 닉네임에서 비어 있는 `1~99`를 무작위 배정하고, 모두 사용 중이면 `100`부터
  순차 배정한다.
- `identification_code`는 공개 가능한 자연수이며 강한 인증 수단으로 간주하지 않는다.
- `preference_type`과 네 유형 점수는 성향 검사 전에는 모두 null이고, 검사 완료 후에는
  모두 값이 있어야 한다.
- `preference_type`은 `THINKER`, `FOODIE`, `ARTIST`, `REMEMBERER` 중 하나이며,
  네 유형 점수는 각각 `thinker_score`, `foodie_score`, `artist_score`,
  `rememberer_score`에 저장한다.
- `Mbti`와 `Team`을 직접 참조하지 않는다.

인증 구현과 토큰·세션 저장 방식은 별도 인증 설계 범위다. 이 문서는 인증 방식이
제공하는 현재 `User` 컨텍스트와 업무 데이터의 관계만 정의한다.

### `questions`, `question_options`

- 질문 하나는 순서가 있는 선택지 네 개를 가진다.
- 선택지는 사색러·미식러·예술러·기억러 네 유형 가중치를 가진다.
- 서비스는 21개 질문 풀에서 7개를 선별하며 동점이면 추가 질문으로 결정한다.

### `places`

- 이름, 카테고리, 단일 `TravelMbti` 유형, 태그 배열, 주소, 좌표, 운영시간, 설명,
  썸네일과 활성 상태를 보존한다.
- `travel_mbti_type`은 사용자 `preference_type`과 같은
  `THINKER`, `FOODIE`, `ARTIST`, `REMEMBERER` 값 중 하나를 저장한다.
- `tags`는 PostgreSQL JSONB 문자열 배열로 저장한다.
- 5·18 연관 의미는 별도 컬럼 없이 검수된 `description`에 포함한다.
- 외부 제공자와 콘텐츠 ID, 추천 점수, 동기화 시각은 저장하지 않는다.
- 코스가 참조하는 Place는 hard delete하지 않는다.
- 방문 인증 반경 100m를 검증하려면 위도·경도에 소수점 이하 최소 6자리 정밀도가
  필요하다. 현재 `numeric(38,2)` 스키마는 후속 마이그레이션 전까지 이 기준을
  충족하지 못한다.

### `recommendation_sets`

- `user_id`가 유일하며 사용자마다 현재 추천 세트 하나만 둔다.
- 여행 기간, 실제 시작·종료일, 순서가 있는 추천 Place ID 배열과 최종
  좋아요·싫어요 ID 배열을 저장한다.
- 인증 세션이 만료되거나 바뀌어도 같은 사용자가 재인증하면 기존 추천 세트를
  조회할 수 있다.
- 기간을 바꾸면 `recommendation_set_id`는 유지하고 같은 행의 기간·추천 ID를
  덮어쓰며 좋아요·싫어요 배열은 초기화한다.
- 회차당 장소 20곳을 제공하고, 회차가 끝날 때 프런트엔드가 해당 회차의
  좋아요·싫어요를 한 번에 전송한다.
- 회차 종료 시 최소 선택 수를 충족하지 못하면 기존 추천 ID를 제외한 다음 20곳을
  같은 배열 뒤에 붙인다. 제공 가능한 장소가 남아 있는 동안 최소 기준 충족까지
  반복한다.
- `recommendation_items` 하위 테이블은 두지 않는다.

상태 소유권과 회차 경계는
[ADR-0008](../adr/0008-recommendation-batches.md)을 따른다.

### `courses`, `course_places`

- AI 생성 성공 시 `Course(DRAFT)`와 `CoursePlace`를 저장하고 `course_id`를 발급한다.
- 여행 기간은 `DAY_TRIP`, `ONE_NIGHT_TWO_DAYS`, `TWO_NIGHTS_THREE_DAYS`,
  `CUSTOM`으로 구분한다.
- 모든 코스는 `start_date`, `end_date`, `start_time`을 저장한다. `CUSTOM`은 3박 4일
  이상 직접 선택 기간이다.
- Course는 제목, 소유자, 상태, 공유 만료 시각과 서버 관리 AI 수정 횟수를 가진다.
- AI 수정은 최대 2회이며 조건부 갱신으로 동시 초과를 막는다.
- CoursePlace는 Place 참조, 일자, 일자 내 순서, 예상 체류시간과 이전 장소에서의
  이동수단을 가진다.
- `(course_id, place_id)`와 `(course_id, day_number, visit_order)`는 유일하다.
- 폴리라인과 경로 계산 결과는 저장하지 않는다. 프런트엔드는 Kakao Maps API로
  지도를 렌더링하고 CoursePlace 순서와 좌표로 TMAP API의 도보 경로를 조회한다.

외부 지도·경로 제공자 경계는
[ADR-0009](../adr/0009-kakao-map-tmap-walking-route.md)을 따른다.

### `explorations`, `exploration_participants`

- Course 하나에는 전체 생명주기에서 Exploration이 최대 하나 존재한다.
- Course 확정 시 `Exploration(BEFORE)`과 생성자 `Participant(OWNER, ACTIVE)`를
  생성한다.
- 별도 `Team` 테이블 없이 Participant가 역할, 상태, 표시 이름과 위치 공유 동의를
  소유한다.
- 사용자는 `BEFORE`와 `ONGOING`을 합쳐 활성 Participant를 최대 하나만 가진다.
- 같은 Exploration의 활성 Participant는 탐험을 시작할 수 있으며 최초 요청만
  `BEFORE → ONGOING` 전환에 성공한다.
- 시작한 Participant는 `started_by_participant_id`로 기록한다.
- 지도 이탈 시 Participant를 `LEFT`로 바꾸고 기존 방문 기록은 보존한다.

### `visits`, `visit_photos`

- Visit은 `participant_id`, `place_id`, 선택적 `course_place_id`, `visited_at`을
  가진다. `course_place_id`가 없으면 코스에 포함되지 않은 주변 장소 방문이다.
- Participant는 Exploration 하나에 속하므로 `(participant_id, place_id)`가 유일해
  같은 탐험에서 같은 참여자의 동일 장소 재인증을 막는다.
- 서버는 요청 GPS와 Place 좌표의 거리를 검사하지만 원본 GPS는 저장하지 않는다.
- 팀 코스 장소 완료 여부와 완료율은 해당 CoursePlace 문맥의 Visit 존재 여부로
  계산한다. 한 명이 먼저 인증해 팀 완료가 된 뒤에도 다른 참여자는 같은 Place의
  개인 Visit을 남길 수 있다.
- 팀 방문 기록과 팀 누적 밝힌 지도는 해당 Exploration의 모든 Participant Visit을
  합쳐 계산한다. CoursePlace 문맥이 없는 주변 장소 Visit도 두 조회에는 포함하지만
  코스 완료율에서는 제외한다.
- Visit에는 사진을 선택적으로 여러 장 연결할 수 있다.
- 사진 파일은 객체 저장소, DB에는 비공개 `object_key`와 표시 순서를 저장한다.
- `(visit_id, display_order)`는 유일하다.

방문 대상과 조회 파생 규칙은
[ADR-0010](../adr/0010-place-based-visits.md)을 따른다.

## 상태 전환

### Course와 Exploration

```text
AI 생성 성공
→ Course(DRAFT)
→ AI 또는 직접 수정
→ Course(CONFIRMED) + Exploration(BEFORE) + Owner Participant(ACTIVE)
→ 공유 링크 생성 시 share_expires_at 설정
→ 활성 Participant의 시작 요청
→ Exploration(ONGOING)
→ 전체 CoursePlace 팀 완료 또는 OWNER 조기 완료 요청
→ Exploration(COMPLETED)
```

- `CONFIRMED` Course와 CoursePlace는 직접 수정할 수 없다.
- 다른 참여자가 합류하기 전에는 확정을 취소해 Course를 `DRAFT`로 되돌리고 빈
  Exploration과 생성자 참여를 정리할 수 있다.
- 공유 URL은 `/explore/{course_id}`로 조합하며 문자열을 저장하지 않는다.
- 링크 생성 시 3일 뒤를 `share_expires_at`으로 저장하고, 재발급 시 같은 URL의
  만료 시각만 연장한다.
- 모든 CoursePlace에 팀 Visit이 존재하면 Exploration을 자동 완료한다.
- `OWNER Participant`만 미방문 CoursePlace가 남아 있어도 탐험을 조기 완료할 수 있다.

### Participant

```text
합류 → ACTIVE
기존 지도 이탈 → LEFT
Exploration 완료 → COMPLETED
```

새 지도 합류 시 기존 `ACTIVE` 참여가 있으면 기존 지도로 돌아가거나 기존 참여를
`LEFT`로 바꾼 뒤 합류해야 한다.

## 데이터와 외부 시스템 경계

| 저장소·시스템 | 책임 |
|---|---|
| PostgreSQL | 사용자, 질문, 장소, 추천 결과, 코스, 탐험, 방문과 사진 메타데이터 |
| 프런트엔드 로컬 스토리지 | 가입 전 검사 결과, 현재 카드, 되돌리기, 일괄 전송 전 반응 |
| 한국관광공사 OpenAPI | 초기 광주 장소 수집 입력. 런타임 정본이 아님 |
| Kakao Maps API | 프런트엔드 지도·핀·뷰포트 렌더링 |
| TMAP API | 프런트엔드 도보 경로와 폴리라인 계산 |
| 객체 저장소 | 방문 인증 사진 원본 |
| WebSocket | 방문 완료·팀 진행과 동의한 참여자의 일시적 위치 이벤트 전파. 위치 이벤트는 10m 이동 기준으로 갱신 |

AI 요청 중에는 프런트엔드가 버튼을 비활성화하고 자동 재시도하지 않는다. MVP는
서버 영속 멱등성 키를 두지 않으므로 네트워크 중복까지 보장하지 않는다.

## 무결성과 오류 처리

- 식별코드 배정은 `(nickname, identification_code)` 유일 제약 충돌 시 재시도한다.
- 추천 세트에 저장하는 Place ID는 모두 `places` 존재 여부를 검증한다.
- Course 확정·취소, Exploration 시작과 AI 수정 카운트는 현재 상태를 조건으로
  갱신한다.
- 공유 링크 만료는 `share_expires_at`으로 판정하며 만료된 신규 합류 요청은 410으로
  거부한다.
- 방문 인증은 Participant가 활성 상태이고 Place가 유효한지 검사한다. CoursePlace
  문맥이 있으면 같은 Exploration의 Course에 포함되는지도 한 트랜잭션에서 검사한다.
- 사진 업로드 실패는 Visit을 취소하지 않는다. 객체 업로드 후 DB 저장이 실패하면
  객체 삭제를 시도하고 남은 고아 객체는 운영 정리 대상으로 처리한다.

## 운영 배포 구조

백엔드는 AWS ALB 뒤의 ECS Fargate에서 실행하고 RDS PostgreSQL을 서버 데이터 정본으로
사용한다. GitHub Actions는 OIDC로 AWS 역할을 맡아 `main` 변경 후 새 컨테이너 이미지를
자동 배포한다. 구조와 승인 결정은
[ADR-0011](../adr/0011-aws-main-continuous-deployment.md), 상태 확인과 복구는
[AWS 배포·운영 절차](../operations/deployment.md)를 따른다.

## 현재 구현과 스키마 변경

- 현재 JPA Entity와 API 골격은 목표 구조와 다를 수 있다. 기능별 진행 상태는
  [백엔드 MVP 상태](../product/mvp.md)에서 확인한다.
- Hibernate는 `ddl-auto=validate`로 Entity와 스키마를 검증하고 Flyway version
  migration으로 스키마를 변경한다.
- 적용된 migration은 수정하지 않고 새 버전 파일을 추가한다. 새 테이블과 nullable FK를
  먼저 추가하는 additive migration을 우선한다.
- `V2__place_based_visits.sql`이 기존 CoursePlace 기반 Visit의 `place_id`를 보강하고
  `course_place_id`를 nullable로 전환했으며, `(participant_id, place_id)` 유일 조건을
  적용한다.
- 방문 인증 구현 전 `places.latitude`, `places.longitude`를 소수점 이하 최소 6자리를
  보존하는 타입으로 변경해야 한다. 좌표 정밀도 변경은 별도 후속 작업이다.
- 기존 테이블 삭제나 공유 Docker volume 초기화는 별도 구현 계획과 실행 직전 승인을
  거친다.

## 아직 확정되지 않은 정책

추천 회차, 참여자별 방문, 인증 반경과 완료 방식은 기능 명세에서 확정되었다. 남은
API 상세와 운영 정책은 [제품 논의 필요](../product/open-questions.md)를 정본으로
사용한다.

## 갱신 규칙

- 제품 정책이 바뀌면 기능 명세와 이 문서를 같은 Pull Request에서 갱신한다.
- 코드가 목표 구조를 구현하면 백엔드 MVP 상태의 근거와 상태도 함께 갱신한다.
- 새로운 테이블 관계나 데이터 소유권 변경은 설계 승인을 먼저 받는다.
