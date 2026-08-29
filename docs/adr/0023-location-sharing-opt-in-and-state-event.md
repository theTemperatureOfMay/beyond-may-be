---
status: accepted
decision-date: 2026-08-28
recorded-date: 2026-08-28
---

# ADR-0023 위치 공유 옵트인은 REST로 저장하고 커밋 후 탐험 상태 이벤트로 알린다

[ADR-0022](0022-authenticated-stomp-transport-foundation.md)는 인증된 STOMP 연결 기반을
마련했지만 기능별 destination, 참여자 인가와 메시지 계약은 남겨 두었다. 기능 4.3.2의
명시적 위치 공유 동의와 기존 마커 제거를 위해 저장 계약과 첫 상태 이벤트 채널을 함께
결정한다.

### 결정

- 인증된 현재 사용자는
  `PATCH /api/v1/explorations/{explorationId}/participants/me/location-sharing`에
  boolean `enabled`만 보내 자신의 설정을 바꾼다. 사용자·참여자 ID는 요청 본문에서 받지
  않는다.
- `BEFORE` 또는 `ONGOING` 탐험의 `ACTIVE Participant`만 변경할 수 있다. 탐험·참여자
  없음은 404, 비활성 참여자는 403, 완료된 탐험은 409로 거부한다.
- 위치 공유 기본값은 `false`다. PostgreSQL에는 Participant의 동의 설정만 저장하고
  실시간 좌표는 저장하지 않는다. 같은 값 요청은 200으로 현재 상태를 반환하되 DB 갱신,
  `updatedAt` 변경과 이벤트 발행을 하지 않는다.
- 실제 변경 커밋 후 `/topic/explorations/{explorationId}/events`에 다음 형태의
  `LOCATION_SHARING_CHANGED`를 발행한다.

```json
{
  "eventId": "bc54fae8-c321-4e87-8573-d4e9a205a8bd",
  "eventType": "LOCATION_SHARING_CHANGED",
  "explorationId": 44,
  "occurredAt": "2026-08-15T21:20:00+09:00",
  "data": {
    "participantId": 72,
    "enabled": true
  }
}
```

- 이벤트는 트랜잭션 커밋 후 최선 노력으로 전송한다. broker 전송 실패는 경고 로그로
  남기고 성공한 REST 응답과 DB 설정을 되돌리지 않는다. outbox, 자동 재시도와 replay는
  두지 않으며 클라이언트는 `eventId`로 중복을 제거한다.
- 위 상태 이벤트 destination 구독은 해당 탐험의 `ACTIVE Participant`에게만 허용한다.
  이 결정 시점에는 다른 `SUBSCRIBE`와 모든 클라이언트 `SEND`를 계속 거부했다. 후속
  [ADR-0026](0026-ephemeral-stomp-location-sharing.md)이 `/locations` 전송·구독만 별도
  권한으로 연다.
- `enabled=false` 변경 이벤트를 받은 클라이언트는 기존 마커를 제거한다. 실시간 위치
  `SEND`가 후속 구현될 때 서버는 매 이벤트마다 현재 동의 상태를 확인해 비동의 좌표를
  수락·전파하지 않아야 한다.

이 결정은 ADR-0022의 연결·인증 기반을 유지하면서 처음으로 기능별 상태 destination과
구독 인가를 연다. 실시간 위치 좌표와 방문 이벤트 계약은 확정하지 않는다.

### 검토한 대안

- 설정 자체를 STOMP `SEND`로 바꾸면 영속 설정의 HTTP 오류·멱등 계약이 불명확해져
  사용하지 않는다.
- 커밋 전에 이벤트를 보내면 롤백된 설정이 다른 클라이언트에 노출될 수 있어 사용하지
  않는다.
- outbox·외부 broker·자동 재시도는 단일 인스턴스 MVP와 ADR-0006의 범위를 넘으므로
  사용하지 않는다. 이벤트 유실이 허용되지 않는 요구가 생기면 다시 결정한다.
- 실시간 좌표를 PostgreSQL에 저장하면 동의 설정과 휘발성 전송 데이터의 수명이 섞이므로
  저장하지 않는다.

### 영향 대상

- 변경 유형: `product`, `api`, `data`, `architecture`, `security`
- 변경한 대상: 위치 공유 PATCH Controller·DTO·Service·Participant·Repository와 오류 코드,
  커밋 후 상태 이벤트 DTO·publisher, STOMP 상태 destination 참여자 인가, 관련 테스트,
  탐험 기능 명세·백엔드 MVP 상태·아키텍처·제품 논의 문서와 Postman Collection
- 확인했지만 변경하지 않음: 기존 Participant column과 Flyway migration, 탐험 시작
  이벤트 계약, HTTP·STOMP bearer 인증 방식, 방문·여행 기록 기능 문서, ADR-0021·ADR-0022
- 확인하지 못함: 프런트엔드 토글·마커 제거와 `eventId` 중복 제거 구현, 운영 reverse
  proxy와 실제 broker 전달 상태
- 미해결: 실시간 위치 좌표 `SEND`·구독 payload, 10m·정확도 필터의 서버 적용 위치,
  방문 상태 이벤트와 재연결 시 상태 복구
- 복구: 코드와 문서는 Git으로 복구할 수 있다. migration과 좌표 저장이 없어 데이터
  복구 절차는 없다.

### 변경 영향 검사

- 검사 스킬: `change-impact-review`
- 검사 일자: 2026-08-28
- 검사 결과: `통과`
- 검사 근거: 탐험 기능 명세·MVP 상태·백엔드 아키텍처·제품 논의·ADR-0021~0023,
  Controller·DTO·Service·Converter·Participant·STOMP 인가·상태 이벤트 publisher와 관련
  테스트·Postman을 대조했다. Standards·Spec 재검사는 각각 finding 0건이었고, UTC 환경의
  탐험 시작 서비스 테스트 4개와 전체 테스트 291개가 실패·건너뜀 없이 통과했다.
  `spotlessCheck`, 전체 `build`, 하네스 semantic 검사, Postman JSON 파싱과
  `git diff --check`도 통과했다. 프런트엔드와 운영 reverse proxy·실제 broker 전달 상태는
  확인하지 않았다.

### 관련 문서

- [ADR-0021](0021-remove-premature-exploration-realtime-implementation.md)
- [ADR-0022](0022-authenticated-stomp-transport-foundation.md)
- [ADR-0026](0026-ephemeral-stomp-location-sharing.md)
- [백엔드 아키텍처](../architecture/backend.md)
- [백엔드 MVP 상태](../product/mvp.md)
- [제품 논의 필요](../product/open-questions.md)
- [탐험 기능 명세](../product/features/exploration.md)
