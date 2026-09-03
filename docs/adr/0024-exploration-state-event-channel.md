---
status: accepted
decision-date: 2026-08-28
recorded-date: 2026-08-28
---

# ADR-0024 탐험 상태 변경은 참여자 전용 STOMP 이벤트 채널로 전파한다

[ADR-0023](0023-location-sharing-opt-in-and-state-event.md)은 위치 공유 설정 변경을 통해
첫 탐험 상태 이벤트 destination을 열었다. 팀 합류·탐험 시작·탐험 완료도 같은 화면
상태를 바꾸므로 공통 envelope, 권한, 커밋 경계와 재접속 복구 규칙을 하나의 계약으로
확정한다.

### 결정

- 클라이언트는 `/topic/explorations/{explorationId}/events`를 구독한다. 해당 탐험의
  현재 `ACTIVE Participant`만 구독할 수 있으며 탐험 ID 형식 오류, 탐험·참여 관계 없음,
  비활성 참여자는 STOMP `ERROR`로 거부한다.
- 메시지 `Content-Type`은 `application/json`이고 모든 이벤트는 `eventId`, `eventType`,
  `explorationId`, `occurredAt`, `data`를 갖는 envelope를 사용한다.
- 이벤트 유형과 `data`는 다음과 같다.
  - `PARTICIPANT_JOINED`: `participantId`, `displayName`, `role`, `participantCount`
  - `EXPLORATION_STARTED`: `status`, `startedByParticipantId`, `startedAt`
  - `LOCATION_SHARING_CHANGED`: `participantId`, `enabled`
  - `EXPLORATION_COMPLETED`: `status`, `completedAt`, `completionReason`
- `completionReason`은 `ALL_COURSE_PLACES_VISITED` 또는
  `OWNER_EARLY_COMPLETION`을 사용한다.
- 상태 변경 이벤트는 업무 트랜잭션 커밋 후 최선 노력으로 발행한다. broker 전송 실패는
  경고 로그로 남기고 이미 커밋된 상태를 되돌리지 않는다.
- 새 Participant 행을 생성한 합류만 `PARTICIPANT_JOINED`를 발행한다. 기존 `ACTIVE`
  참여자의 재진입과 `LEFT` 참여자의 재활성화에는 발행하지 않는다.
- 이 ADR 구현으로 `PARTICIPANT_JOINED`, `EXPLORATION_STARTED`,
  `LOCATION_SHARING_CHANGED` 생산자를 연결하고 `EXPLORATION_COMPLETED` envelope와 커밋 후
  publisher를 제공한다. 후속 ADR-0025는 `ALL_COURSE_PLACES_VISITED` 자동 완료 생산자를
  연결했으며, 기능 5.1.2 구현은 인증
  `POST /api/v1/explorations/{explorationId}/complete`에서
  `OWNER_EARLY_COMPLETION` 생산자를 연결한다.
- 이벤트 replay를 보장하지 않는다. 재접속한 클라이언트는 탐험 상세와 참여자 목록 HTTP
  API로 정본을 다시 조회하고 같은 `eventId`를 중복 반영하지 않는다.
- 방문 인증 이벤트는 별도 `/visits` 채널로 분리하고 위치 좌표는 이 상태 채널에 포함하지
  않는다. 이 ADR은 방문 채널과 위치 좌표 payload를 구현하지 않으며, 후속
  [ADR-0025](0025-visit-confirmation-and-realtime-propagation.md)와
  [ADR-0026](0026-ephemeral-stomp-location-sharing.md)이 각각 계약을 확정한다.

이 결정은 ADR-0022의 연결 인증과 ADR-0023의 위치 공유 저장·발행 경계를 유지하면서
탐험 화면 상태 이벤트 계약을 완성한다.

### 검토한 대안

- 이벤트 유형마다 destination을 나누면 클라이언트 구독과 권한 검사가 반복되므로 같은
  탐험의 저빈도 상태 변경은 하나의 채널로 묶는다. 고빈도 좌표와 별도 생명주기의 방문
  인증은 분리한다.
- 커밋 전에 발행하면 롤백된 상태가 보일 수 있어 사용하지 않는다.
- outbox, 자동 재시도, replay와 외부 broker는 현재 단일 인스턴스 MVP와 ADR-0006의
  범위를 넘으므로 도입하지 않는다. 이벤트 유실이 허용되지 않거나 다중 인스턴스 전달이
  필요해질 때 다시 결정한다.
- STOMP 이벤트를 정본으로 사용하면 재접속 중 유실을 복구할 수 없으므로 HTTP 조회를
  정본으로 유지한다.

### 영향 대상

- 변경 유형: `product`, `api`, `data`, `architecture`, `security`
- 변경한 대상: 참여자 합류 이벤트 DTO·converter·Service, 완료 이벤트 DTO, 커밋 후 상태
  event publisher, 동시 합류와 OWNER 조기 완료를 직렬화하는 탐험 행 잠금과 회귀 테스트,
  STOMP JSON·ERROR 통합 테스트와 관련 단위 테스트, 탐험·여행 기록 기능 명세, 백엔드 MVP
  상태·아키텍처·제품 논의 문서와 Postman Collection
- 확인했지만 변경하지 않음: STOMP endpoint·CONNECT 인증·상태 destination 구독 인가,
  기존 탐험 시작·위치 공유 설정 생산자, 탐험·참여자 HTTP 조회 계약, DB migration
- 확인하지 못함: 프런트엔드 `eventId` 중복 제거와 재접속 복구, 운영 reverse proxy와 실제
  broker 전달 상태
- 미해결: 다중 인스턴스 이벤트 전달
- 복구: 코드와 문서는 Git으로 복구할 수 있다. DB 변경이 없어 데이터 복구 절차는 없다.

### 변경 영향 검사

- 검사 스킬: `change-impact-review`
- 검사 일자: 2026-09-03
- 검사 결과: `통과`
- 검사 근거: ADR-0022·0023과 후속 ADR-0025·0026, 탐험·여행 기록 기능 명세, MVP·백엔드
  아키텍처·제품 논의 문서, OWNER 조기 완료 HTTP·이벤트와 Postman 계약을 구현·테스트와
  대조했다. 전체 413개 테스트, `spotlessCheck`, 전체 `build`, Postman Collection·saved body
  JSON 파싱과 하네스 semantic 검사가 통과했고, 프런트엔드·운영 broker는 확인하지 못한
  대상으로 위 영향 목록에 남겼다.

### 관련 문서

- [ADR-0021](0021-remove-premature-exploration-realtime-implementation.md)
- [ADR-0022](0022-authenticated-stomp-transport-foundation.md)
- [ADR-0023](0023-location-sharing-opt-in-and-state-event.md)
- [ADR-0025](0025-visit-confirmation-and-realtime-propagation.md)
- [ADR-0026](0026-ephemeral-stomp-location-sharing.md)
- [백엔드 아키텍처](../architecture/backend.md)
- [백엔드 MVP 상태](../product/mvp.md)
- [제품 논의 필요](../product/open-questions.md)
- [탐험 기능 명세](../product/features/exploration.md)
- [여행 기록 기능 명세](../product/features/travel-records.md)
