# 5. 팀 내 여행 기록

[전체 기능 명세](../feature-spec.md) · [백엔드 MVP 상태](../mvp.md) · [논의 필요](../open-questions.md)

## 상세 기능

### 5.1 코스 기록

#### 5.1.1 진행 중인 코스 조회

- 적용 사용자: 닉네임 세션 사용자

##### 상세 동작

- 여행 기록 탭의 '진행 중' 섹션 진입 시 탐험을 시작했고 아직 완료되지 않은 코스 목록 표시
- 카드 구성: 코스명, N / M 장소 방문, 팀원 수
- 카드 탭 시 해당 코스의 4.3.1 탐험 지도로 복귀
- 진행 중 코스 없는 경우: "아직 탐험 중인 코스가 없습니다" 빈 상태 UI 표시
- 동시 진행 가능 코스 수: 1개

##### 백엔드·기획 참고

동시 진행 가능 코스 수 → 1개

탐험 시작 전은 `BEFORE`, 시작 후는 `ONGOING`으로 구분하며 진행 중 목록에는
`ONGOING`만 표시

인증된 현재 ACTIVE 참여자는 `GET /api/v1/explorations?status=ONGOING`으로 진행 중
탐험을 최대 1개 조회한다. 응답은 코스명, `LEFT`를 제외한 팀원 수·표시 이름, 팀 기준
코스 장소 방문 수와 전체 장소 수, 코스 순서상 첫 유효 대표 이미지와 시작 시각을 포함한다.
주변 장소 Visit은 코스 진행률에서 제외하며 결과가 없으면 빈 배열과 `totalCount: 0`을
반환한다. LEFT 참여는 진행 중 목록에서 제외하며, 현재 ACTIVE인 `ONGOING` 탐험이
둘 이상이면 서버 오류로 불변식 위반을 드러낸다. 완료 목록은 과거 참여도 포함한다. 단일 탐험의
상태와 실행 권한은 `GET /api/v1/explorations/{explorationId}`로 조회한다.

#### 5.1.2 완료한 코스 조회

- 적용 사용자: 닉네임 세션 사용자

##### 상세 동작

- 여행 기록 탭의 '완료' 섹션 진입 시 완료 상태인 코스 목록 표시
- 최신 완료 순(내림차순) 정렬
- 카드 구성: 코스명, 완료 날짜, 팀원 닉네임 목록, 대표 이미지
- 완료 코스 없는 경우: "아직 완료한 코스가 없습니다" 빈 상태 UI 표시
- 완료 코스 보존 기간: 평생

##### 백엔드·기획 참고

완료 코스 보존 기간 → 평생

장소 완료 판정은 팀 기준으로 한다. 같은 코스 장소에 팀원 중 한 명의 Visit이 있으면
해당 장소를 팀 완료로 계산한다.
전체 코스 장소가 팀 완료되면 자동으로 탐험을 완료한다.
현재 탐험 소유자(OWNER)는 전체 장소를 방문하기 전에도 별도 '코스 완료' 버튼으로 탐험을 완료할 수 있다. 최초 OWNER가 이탈해 역할을 넘긴 경우에는 승계자가 이 권한을 갖는다.

인증된 현재 또는 과거 참여자는 `GET /api/v1/explorations?status=COMPLETED`로 완료 탐험을
`completedAt` 내림차순 조회한다. 응답 카드와 빈 결과 규칙은 5.1.1과 같고 완료 시각을
추가로 포함하며 기록을 기간 제한 없이 보존한다. 완료된 단일 탐험도
`GET /api/v1/explorations/{explorationId}`로 조회할 수 있다. 전체 장소 방문에 따른 자동
완료는 방문 저장 흐름이 수행한다. 마지막 팀 CoursePlace 방문은 Exploration과 활성
Participant를 완료하고 커밋 후 `/topic/explorations/{explorationId}/events`에 이유
`ALL_COURSE_PLACES_VISITED`인 `EXPLORATION_COMPLETED`를 전파한다.

미방문 CoursePlace가 남아 있으면 프런트엔드는 확인 모달에서 OWNER의 최종 확인을 받은 뒤
요청 본문과 query 없이 인증 `POST /api/v1/explorations/{explorationId}/complete`를 호출한다.
서버 확인 단계는 별도로 두지 않는다. 서버는 `ONGOING Exploration`의 현재 `ACTIVE OWNER`
요청만 허용하고 Exploration 행 잠금 안에서 `completedAt`을 기록하며 모든 `ACTIVE
Participant`를 `COMPLETED`로 전환한다. Visit과 사진은 보존한다. 응답은 탐험·코스 ID,
`COMPLETED`, `OWNER_EARLY_COMPLETION`, 완료 시각과 완료 직전 팀 코스 진행률을 포함한다.
완료 커밋 후 같은 상태 채널에 이유가 `OWNER_EARLY_COMPLETION`인
`EXPLORATION_COMPLETED`를 전파한다. 탐험이 없으면 404, 현재 `ACTIVE OWNER`가 아니면
403, `ONGOING`이 아니거나 이미 완료됐으면 409를 반환한다. 모든 CoursePlace가 이미 팀
완료된 경우 방문 저장 흐름이 자동 완료하므로 프런트엔드는 이 API를 호출하지 않는다
([ADR-0024](../../adr/0024-exploration-state-event-channel.md),
[ADR-0025](../../adr/0025-visit-confirmation-and-realtime-propagation.md)).

### 5.2 방문 기록

#### 5.2.1 방문한 장소 목록 조회

- 적용 사용자: 닉네임 세션 사용자

##### 상세 동작

- 여행 기록 탭의 '방문 장소' 섹션 진입 시 4.3.3을 통해 인증 완료한 전체 장소 목록 표시
- 방문 시간 내림차순 정렬
- 목록 항목 구성: 장소 썸네일, 장소명, 방문 시각, 카테고리 태그
- 항목 탭 시 4.4.2 장소 상세 하단 시트 표시
- 방문 기록 없는 경우: "아직 방문한 장소가 없습니다" 빈 상태 UI 표시
- 방문 기록 목록은 팀 전체 기준으로 표시

##### 백엔드·기획 참고

방문 기록은 참여자별로 저장한다. 팀원은 같은 장소를 각자 한 번씩 인증할 수 있고,
팀 화면에는 참여자별 기록을 합친 전체 방문 기록을 표시한다.
인증된 현재 또는 과거 참여자는 `GET /api/v1/visits?explorationId={explorationId}`로
해당 탐험의 모든 참여자 Visit을 `visitedAt` 내림차순 조회한다. 코스 장소와 주변 장소를
모두 포함하며 응답에는 방문 참여자의 고정 표시 이름, 장소 정보, CoursePlace 연결 여부와
사진을 담는다. 사진은 `displayOrder` 오름차순이고 저장된 object key마다 새 presigned GET
URL과 만료 시각을 발급하며 userId와 object key는 노출하지 않는다. 방문이 없으면 빈 배열과
`totalCount: 0`을 반환한다. 탐험이 없으면 404, 참여 이력이 없으면 403으로 거부한다.

방문 인증에는 사진을 선택적으로 첨부할 수 있으며 사진 장수는 제한하지 않는다.
사진은 한 장씩 업로드하고 장당 10MB 이하의 JPEG·PNG·WebP 형식을 허용한다.
Visit을 만든 현재 `ACTIVE Participant` 본인은 인증
`POST /api/v1/visits/{visitId}/photos`의 multipart `file`로 사진을 한 장씩 추가한다.
완료된 탐험에는 추가할 수 없다. 서버가 다음 표시 순서를 배정하고 원본은 비공개 S3,
DB에는 object key와 순서만 저장한다. 성공 응답은 object key 대신 기본 1시간 유효한
presigned GET URL과 실제 만료 시각을 반환한다. 이후 팀 방문 목록 조회는 저장된 key마다
새 URL을 발급한다([ADR-0027](../../adr/0027-private-s3-visit-photo-storage.md)).

(셋로그처럼) 사진찍고 인증 후 백로그 형태로 생성해줌
ex) 포켓몬 고, 피크민처럼

#### 5.2.2 밝힌 지도 전체 보기

- 적용 사용자: 닉네임 세션 사용자

##### 상세 동작

- 여행 기록 탭의 '밝힌 지도' 섹션 진입 시 방문 인증 장소 기반으로 광주 전체 지도 렌더링
- [Before 탐험] 지도 전체 흑백·어두운 상태
- [After 방문 인증 누적] 방문 완료 장소 구역 컬러·밝은 상태로 누적 표시, 방문 장소 핀 오버레이
- 지도 밝기 영역 단위: 장소 단위 핀 강조
- 방문 기록 없는 경우: 전체 어두운 지도 + "아직 밝힌 곳이 없어요" 오버레이 표시
- 지도 이미지 저장/공유 기능 제공 여부: 제공

##### 백엔드·기획 참고

인증된 현재 또는 과거 참여자는
`GET /api/v1/visits/visited-places?explorationId={explorationId}`로 해당 탐험의 모든
Participant Visit을 장소별로 집계해 조회한다. 코스 장소와 주변 장소를 모두 포함하고,
장소 정보·검수 장소 좌표·코스 포함 여부·전체 방문 수·방문 참여자 수·최초 및 최근 방문
시각·참여자 표시 이름을 반환한다. 장소 목록은 최근 방문 시각 내림차순이며 참여자 표시
이름은 해당 장소의 최초 방문 순서다. 참여자별 동일 장소 Visit이 유일하므로 현재
`visitCount`와 `visitedByCount` 값은 같지만 의미를 구분해 제공한다.

방문 검증에 사용한 사용자 GPS 좌표는 저장하거나 반환하지 않는다. 방문이 없으면 빈
`visitedPlaces`와 `totalVisitedPlaceCount: 0`을 반환한다. 탐험이 없으면 404, 참여 이력이
없으면 403으로 거부한다. 지도 렌더링과 이미지 저장·공유는 프런트엔드가 처리하며 별도
백엔드 이미지 API를 만들지 않는다.
