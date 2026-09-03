---
status: accepted
decision-date: 2026-08-28
recorded-date: 2026-08-28
---

# ADR-0025 방문 인증은 REST로 저장하고 전용 STOMP 채널에 커밋 후 전파한다

[ADR-0021](0021-remove-premature-exploration-realtime-implementation.md)은 계약이 확정되지
않은 방문 API와 이벤트를 제거하고 Place 기반 Visit 스키마만 남겼다. 이후 확정된 기능
4.3.3·4.3.4 계약에 따라 방문의 저장 경계, 팀 최초 방문과 코스 진행률 계산, 자동 완료,
실시간 전파와 재접속 복구 책임을 다시 연결한다.

### 결정

- 방문 인증은 인증 `POST /api/v1/visits`로 처리한다. 요청은 `explorationId`, `placeId`,
  `latitude`, `longitude`, `accuracyMeters`를 받고 성공 시 `201 Created`로 생성된 Visit,
  서버 계산 거리, 팀 최초 방문 여부, 코스 진행률과 탐험 상태를 반환한다.
- 서버는 `ONGOING Exploration`의 현재 `ACTIVE Participant`, 활성 Place, GPS 정확도 50m
  이하와 장소 반경 100m 이하를 검증한다. Participant와 CoursePlace 문맥은 서버가
  결정하고 검증에 사용한 좌표·정확도는 저장하지 않는다.
- 같은 Participant의 같은 Place 방문은 한 번만 허용한다. 다른 팀원이 먼저 방문한
  Place도 각 Participant는 개인 Visit을 남길 수 있으며, 팀의 첫 Visit에만
  `teamFirstVisit=true`를 반환한다.
- 코스에 없는 주변 Place도 `coursePlaceId=null`인 Visit으로 저장하고 팀 방문에는
  포함한다. 코스 진행률은 팀 Visit의 고유 CoursePlace만 집계하므로 주변 Place 방문으로
  변하지 않는다.
- 방문 인증은 Exploration 행의 쓰기 잠금 안에서 중복·팀 최초·진행률·자동 완료를
  계산한다. 마지막 미완료 CoursePlace가 팀 방문으로 채워지면 Exploration과 현재
  `ACTIVE Participant`를 `COMPLETED`로 전환한다.
- 커밋된 모든 개인 Visit은
  `/topic/explorations/{explorationId}/visits`에 JSON `VISIT_CONFIRMED` envelope로 한 번
  최선 노력 전파한다. 좌표·정확도·사진은 payload에 포함하지 않는다. 해당 탐험의 현재
  `ACTIVE Participant`만 구독할 수 있다.
- 자동 완료를 만든 Visit의 `explorationStatus`는 `COMPLETED`이고, 같은 트랜잭션은
  상태 채널에 `completionReason=ALL_COURSE_PLACES_VISITED`인
  `EXPLORATION_COMPLETED`도 발행한다. 두 이벤트 listener는 `AFTER_COMMIT`에 동작하며
  broker 실패가 커밋된 업무 상태를 되돌리지 않는다.
- 이벤트 replay를 보장하지 않는다. 재접속 시 탐험 상세 HTTP 조회로 상태와 집계는
  복구한다. 이 결정 당시 개별 방문·핀의 완전한 복구에 필요한 Visit 조회와 사진 업로드는
  범위에서 제외했으며, 후속 ADR-0027과 `GET /api/v1/visits`가 이를 구현했다. OWNER 조기
  완료 API는 여전히 포함하지 않는다.
- 기존 Place 기반 Visit 스키마와 좌표 정밀도가 계약을 충족하므로 Flyway migration은
  추가하지 않는다.

### 검토한 대안

- 방문 인증을 STOMP `SEND`로 처리하면 저장 성공·HTTP 오류 계약과 재시도 경계가
  흐려지므로 REST 명령과 STOMP 알림을 분리한다.
- 상태 이벤트 채널에 방문을 합치면 빈도가 높은 개인 방문과 저빈도 탐험 상태의 소비
  책임이 섞이므로 전용 `/visits` 채널을 사용한다.
- 커밋 전에 broker로 전송하면 롤백된 Visit이 보일 수 있어 Spring 트랜잭션 이벤트의
  `AFTER_COMMIT`을 사용한다.
- outbox·재전송·event replay와 외부 broker는 단일 인스턴스 MVP 범위를 넘는다. 이벤트
  유실을 허용할 수 없거나 다중 인스턴스 전달이 필요해질 때 다시 결정한다.
- 좌표와 사진을 이벤트에 넣으면 개인정보와 payload가 불필요하게 확장되므로 저장 결과와
  팀 화면 갱신에 필요한 필드만 전파한다.

### 영향 대상

- 변경 유형: `product`, `api`, `data`, `architecture`, `security`
- 변경한 대상: 방문 인증 Controller·DTO·Service·Repository·converter, 방문 이벤트
  publisher와 STOMP 구독 인가, Exploration·Participant 완료 전환, 오류 코드와 관련
  단위·통합 테스트, 탐험·여행 기록 기능 명세, 백엔드 MVP·아키텍처·제품 논의 문서와
  Postman Collection
- 확인했지만 변경하지 않음: Visit·VisitPhoto Entity, Flyway V1·V2·V5, 탐험 상세·참여자
  조회 API, 상태 이벤트 공통 destination과 WebSocket `CONNECT` 인증, 사용자 흐름
- 확인하지 못함: 프런트엔드 핀 전환·`eventId` 중복 제거·재접속 복구, 실제 모바일 GPS와
  운영 reverse proxy·broker 전달
- 미해결: OWNER 조기 완료, 다중 인스턴스 이벤트 전달
- 복구: 코드와 문서는 Git으로 복구할 수 있다. migration이 없어 DB rollback 절차는 없다.

### 변경 영향 검사

- 검사 스킬: `change-impact-review`
- 검사 일자: 2026-09-03
- 검사 결과: `통과 (finding 없음)`
- 검사 근거: 기존 방문 REST 저장·STOMP 전파 결정과 후속 팀 방문 기록 GET의 복구 경계를
  탐험·여행 기록 명세, MVP·백엔드 아키텍처·ADR-0027, 코드·테스트·Postman과 대조했다.
  GET 구현은 이벤트 payload나 구독 인가를 바꾸지 않는다. 전체 404개 테스트,
  `spotlessCheck`, 전체 `build`, Postman JSON 파싱, 하네스 semantic·제품 지식 베이스와
  `git diff --check`가 통과했고 명세 코드 검토 finding은 없었다.

### 관련 문서

- [ADR-0021](0021-remove-premature-exploration-realtime-implementation.md)
- [ADR-0022](0022-authenticated-stomp-transport-foundation.md)
- [ADR-0024](0024-exploration-state-event-channel.md)
- [백엔드 아키텍처](../architecture/backend.md)
- [백엔드 MVP 상태](../product/mvp.md)
- [제품 논의 필요](../product/open-questions.md)
- [탐험 기능 명세](../product/features/exploration.md)
- [여행 기록 기능 명세](../product/features/travel-records.md)
