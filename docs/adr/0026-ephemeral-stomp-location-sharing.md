---
status: accepted
decision-date: 2026-08-28
recorded-date: 2026-08-28
---

# ADR-0026 팀원 위치는 전용 STOMP 채널에서 세션 단위로 필터링해 휘발성 전파한다

[ADR-0023](0023-location-sharing-opt-in-and-state-event.md)은 위치 공유 동의를 저장하고
설정 변경을 상태 채널에 전파했지만, 실제 좌표의 전송·구독·필터 계약은 남겨 두었다.
기능 4.3.1·4.3.2의 현재 위치와 옵트인 팀원 마커를 위해 인증·인가, 입력 경계와 휘발성
상태의 수명을 확정한다.

### 결정

- 인증된 클라이언트는 `application/json` STOMP `SEND`로
  `/app/explorations/{explorationId}/locations`에 `latitude`, `longitude`,
  `accuracyMeters`, `recordedAt`을 보낸다. 좌표와 정확도는 JSON 숫자만 허용하며 위도
  `-90..90`, 경도 `-180..180`, 정확도 `0..50m`, 시각은 offset을 포함한
  `OffsetDateTime`이어야 한다. 네 필드 외의 미지 필드와 숫자 문자열은 거부한다.
- 서버는 STOMP `CONNECT`에서 확인한 userId로 `ONGOING Exploration`의 현재
  `ACTIVE Participant`를 찾는다. 위치 공유 동의가 `true`인 참여자의 유효한 좌표만
  수락하며 클라이언트가 보낸 사용자·참여자 식별자는 받지 않는다. 위치 수락
  트랜잭션은 탐험 행과 참여자 행을 그 순서로 잠가 공유 설정 변경·탐험 완료와
  직렬화하므로, 해당 변경이 먼저 커밋된 뒤 과거 상태로 좌표를 늦게 전파하지 않는다.
- 연결별 마지막 수락 위치가 없거나 그 위치에서 10m 이상 이동했을 때만 새 위치를
  수락한다. 10m 미만 이동은 오류 없이 무시하고, 연결 종료 시 해당 세션의 마지막 위치를
  제거한다. 위치 공유 설정이 실제 변경되어 커밋되면 해당 참여자의 모든 연결 기준점도
  제거하므로 다시 동의한 뒤의 첫 유효 위치는 즉시 전파한다.
- 수락한 위치는 `/topic/explorations/{explorationId}/locations`에 다음 JSON envelope로
  즉시 전파한다. 해당 `ONGOING Exploration`의 현재 `ACTIVE Participant`만 구독할 수
  있다.

```json
{
  "eventId": "3bc9ee88-0ac3-44b3-8de1-78481720a1a2",
  "eventType": "LOCATION_UPDATED",
  "explorationId": 44,
  "occurredAt": "2026-08-15T14:35:00+09:00",
  "data": {
    "participantId": 72,
    "displayName": "김감자감자",
    "latitude": 35.1402,
    "longitude": 126.9124,
    "accuracyMeters": 18.5,
    "recordedAt": "2026-08-15T14:34:59+09:00"
  }
}
```

- 이탈 시 공유를 끄고 커밋 후 `LOCATION_SHARING_CHANGED(false)`를 발행해 모든 연결의
  위치 기준점과 화면 마커를 제거한다. 기존 구독도 송신 직전 참여 상태를 확인하며
  LEFT·COMPLETED 참여자에게 위치를 보내지 않는다.
- payload에는 탐험 범위의 `participantId`와 `displayName`만 넣고 userId를 노출하지
  않는다. 메시지 `Content-Type`은 `application/json`이다.
- 좌표와 연결별 마지막 위치는 PostgreSQL에 저장하지 않고 과거 이벤트를 replay하지
  않는다. 재접속 후 위치 마커는 이후 새 이벤트로만 복구한다. 공유를 끈 참여자의 기존
  마커는 `/events` 채널의 `LOCATION_SHARING_CHANGED(enabled=false)`로 제거한다.
- 탐험 ID 형식, JSON·필드 검증, Content-Type, 탐험 없음·상태, 참여 상태, 공유 동의와
  정확도 오류는 빈 body의 STOMP `ERROR`로 반환한다. `message` header는 각각
  `LOCATION_PAYLOAD_INVALID`, `LOCATION_ACCURACY_EXCEEDED`,
  `EXPLORATION_NOT_ONGOING`, `PARTICIPANT_NOT_ACTIVE`,
  `LOCATION_SHARING_DISABLED`를 사용하고 예상하지 못한 실패는
  `LOCATION_PROCESSING_FAILED`를 사용한다. `ERROR` 뒤에는 연결을 닫으며 서버는
  자동 재전송하지 않는다. 유효한 수락에는 별도 성공 프레임을 보내지 않고 구독 채널의
  `LOCATION_UPDATED`로 확인한다.
- inbound 채널 인터셉터가 인증 인터셉터 다음에 위치 명령을 파싱·검증하고 수락한
  `SEND`를 소비한다. STOMP error handler가 중첩된 계약 예외를 안정 코드로 변환한다.
- MVP는 기존 simple broker와 서버 인스턴스 메모리만 사용한다. Redis, outbox와 외부
  broker는 도입하지 않으며 다중 서버 인스턴스에서 세션 간 위치 전달이 필요해질 때
  별도 broker와 분산 상태를 다시 결정한다.

### 검토한 대안

- 위치를 REST로 보내면 단방향 실시간 세션과 오류 경계가 나뉘고 고빈도 명령에 불필요한
  HTTP 응답이 생기므로 기존 인증 STOMP 연결을 사용한다.
- 위치 명령을 일반 메시지 handler에서 처리하면 handler 예외가 실제 STOMP `ERROR`로
  전달되는 경계가 불명확해져 inbound 채널 인터셉터에서 처리한다.
- `/events` 상태 채널에 좌표를 합치면 저빈도 상태와 고빈도 휘발 위치의 소비·수명이
  섞이므로 전용 `/locations` 채널로 분리한다.
- 좌표나 마지막 위치를 PostgreSQL에 저장하면 화면용 휘발 상태가 정본 데이터가 되므로
  저장하지 않는다.
- MVP부터 Redis·외부 broker를 사용하면 단일 인스턴스 요구보다 운영 복잡도가 커지므로
  실제 다중 인스턴스 요구가 생길 때 추가한다.

### 영향 대상

- 변경 유형: `product`, `api`, `data`, `architecture`, `security`
- 변경한 대상: 위치 요청·이벤트 DTO와 converter, 상태 변경과 직렬화한 위치 수락·10m
  필터 service, STOMP SEND·SUBSCRIBE 인가와 엄격 JSON 처리·안정 오류 handler, 연결
  종료·공유 설정 변경 기준점 정리, 단위·통합 테스트, 탐험 기능 명세·백엔드 MVP
  상태·아키텍처·제품 논의 문서
- 확인했지만 변경하지 않음: `/ws` endpoint와 CONNECT bearer 인증, 위치 공유 REST 설정과
  `LOCATION_SHARING_CHANGED`, `/events`·`/visits` 계약, Participant·Exploration schema와
  Flyway migration, HTTP Postman Collection
- 확인하지 못함: 프런트엔드 Geolocation 전송·마커 수명 처리, 운영 reverse proxy와 실제
  다중 서버 broker 전달
- 미해결: 다중 서버 인스턴스의 외부 broker·분산 세션 상태, 재접속 전 위치 replay
- 복구: 코드와 문서는 Git으로 복구할 수 있다. DB·migration 변경이 없어 데이터 복구
  절차는 없다.

### 변경 영향 검사

- 검사 스킬: `change-impact-review`
- 검사 일자: 2026-08-29
- 검사 결과: `통과`
- 검사 근거: 담당 API 명세와 ADR-0006·0021~0026, 탐험 기능 명세·MVP 상태·백엔드
  아키텍처·제품 논의, STOMP 설정·인증·위치 입력 처리·Service·DTO·Converter와 관련
  테스트를 대조했다. 코드 재검사에서 확인한 상태 변경 경합, listener 트랜잭션, JSON
  `null`, 빈 탐험 ID와 비-SEND 오류 오분류를 수정한 뒤 Standards·Spec finding은 각각
  0건이었다. 위치 관련 단위·실제 WebSocket 통합 테스트 54개와 ArchUnit·애플리케이션
  컨텍스트를 포함한 전체 346개 테스트가 실패·건너뜀 없이 통과했다. `spotlessCheck`, 전체
  `build`, 제품 지식·하네스 semantic 검사, Postman JSON 파싱과 `git diff --check`도
  통과했다. 프런트엔드와 운영 reverse proxy·실제 다중 서버 broker 전달은 확인하지
  않았다.

### 관련 문서

이탈 확장(2026-09-12): 변경 대상은 이탈 Service, 기존 구독의 송신 권한 검사,
WebSocketConfig·STOMP 테스트, 기능·아키텍처·ADR-0024다. 위치 입력·좌표 형식·10m/50m
필터·DB 비저장·CONNECT 인증·스킬·하네스 규칙은 유지한다. 프런트 마커 제거와 운영
전달은 확인하지 못하며, 기존 다중 인스턴스 전달은 미해결이다.
`change-impact-review` 검사 결과: 통과. 전체 459개 테스트(실패·건너뜀 0), 실제 WebSocket 기존 구독 차단, spotlessCheck와 문서 상대 링크를 검증했다. 코드·문서는 Git 복구 가능하며 migration은 없다.


- [ADR-0022](0022-authenticated-stomp-transport-foundation.md)
- [ADR-0023](0023-location-sharing-opt-in-and-state-event.md)
- [ADR-0024](0024-exploration-state-event-channel.md)
- [ADR-0025](0025-visit-confirmation-and-realtime-propagation.md)
- [백엔드 아키텍처](../architecture/backend.md)
- [백엔드 MVP 상태](../product/mvp.md)
- [제품 논의 필요](../product/open-questions.md)
- [탐험 기능 명세](../product/features/exploration.md)
