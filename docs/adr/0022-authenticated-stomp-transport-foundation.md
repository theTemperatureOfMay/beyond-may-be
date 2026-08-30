---
status: accepted
decision-date: 2026-08-28
recorded-date: 2026-08-28
---

# ADR-0022 기능 4 실시간 전송 기반은 인증된 Spring WebSocket(STOMP)로 설정한다

[ADR-0021](0021-remove-premature-exploration-realtime-implementation.md)은 선행 구현된
Socket.IO 계약을 제거하고 대체 실시간 계약을 별도 결정으로 남겼다. 담당 API 명세에서
Spring WebSocket(STOMP) endpoint와 prefix가 확정됐으므로, 기능 4 메시지 구현에 앞서
연결과 인증 경계만 설정한다.

### 결정

- 기존 `spring-boot-starter-websocket`을 사용하고 STOMP endpoint를 `/ws`로 둔다.
- 단일 인스턴스 MVP에 맞춰 내장 simple broker의 prefix는 `/topic`, 애플리케이션
  destination prefix는 `/app`으로 둔다.
- HTTP WebSocket upgrade의 정확한 `GET /ws` 경로만 Spring Security filter chain에서
  허용한다.
  실제 인증은 STOMP `CONNECT` frame의 native `Authorization: Bearer <token>` 헤더를
  기존 `AuthTokenService`로 검증하고 userId principal을 설정한다.
- 별도 origin 허용 목록을 열지 않아 Spring의 same-origin 기본값을 유지한다. SockJS,
  query token, cookie 인증은 추가하지 않는다.
- 기능별 destination과 `ACTIVE Participant` 인가가 구현되기 전까지 클라이언트의 모든
  `SEND`와 `SUBSCRIBE` frame을 거부한다.
- 이번 결정에는 기능별 메시지 DTO·handler·publisher, 위치 공유, 방문·진행 이벤트와
  REST API·업무 로직을 포함하지 않는다.

이 결정은 ADR-0021의 제거 결정을 유지하면서, 당시 미해결로 남긴 STOMP 전송 기반 중
endpoint·prefix·연결 인증 경계만 후속 결정한다.

### 검토한 대안

- WebSocket handshake query에 토큰을 넣으면 URL·접근 로그에 노출될 수 있어 사용하지
  않는다. 브라우저 WebSocket API가 임의 handshake header를 지원하지 않으므로 STOMP
  `CONNECT` native header를 사용한다.
- Socket.IO를 다시 도입하면 ADR-0021의 제거 사유와 충돌하고 별도 서버·의존성이
  필요하므로 사용하지 않는다.
- 외부 broker나 Redis를 도입하면 현재 단일 인스턴스 MVP 범위를 넘고 ADR-0006의
  분산 상태 비도입 원칙과 맞지 않으므로 사용하지 않는다. 다중 인스턴스 실시간 전달이
  필요해질 때 다시 결정한다.
- 기능별 destination을 미리 허용하면 참여자 권한 검증 없이 공개 계약을 열게 되므로,
  해당 기능 구현 시 허용 목록과 인가 검사를 함께 추가한다.

### 영향 대상

- 변경 유형: `architecture`, `api`, `security`
- 변경한 대상: WebSocket/STOMP broker 설정, `/ws` HTTP 보안 경계, STOMP `CONNECT`
  bearer 인증과 `SEND`·`SUBSCRIBE` 기본 차단, 관련 단위·통합·로그 마스킹 회귀 테스트,
  탐험 기능 명세·백엔드 아키텍처·MVP 상태·제품 논의 문서
- 확인했지만 변경하지 않음: 기능 4 REST API·Service·Entity·migration,
  HTTP bearer 인증 방식, ADR-0021의 제거 결정, Postman Collection
- 확인하지 못함: 프런트엔드 STOMP client, 운영 reverse proxy의 WebSocket upgrade 설정,
  운영 배포 상태
- 미해결: 기능별 destination·payload·재연결 정책, `ACTIVE Participant` 인가, 위치 공유
  opt-in과 방문·진행 이벤트의 REST 연계
- 복구: 코드와 문서는 Git으로 복구할 수 있다. DB 변경이 없어 데이터 복구 절차는 없다.

### 변경 영향 검사

- 검사 스킬: `change-impact-review`
- 검사 일자: 2026-08-28
- 검사 결과: `통과` (Docker가 필요한 전체 Testcontainers 검사는 환경 제약 별도 기록)
- 검사 근거: ADR-0021·ADR-0022, 탐험 기능 명세, 백엔드 MVP 상태·아키텍처·제품 논의,
  `SecurityConfig`, WebSocket/STOMP 설정·인증 interceptor, 기존 HTTP 토큰 인증·로그
  마스킹과 관련 테스트를 대조했다. Standards·Spec 재검사는 각각 finding 0건이었고,
  STOMP 인증·명령 차단·정확한 `GET /ws` 공개·same-origin·로그 마스킹 관련 17개 테스트,
  `spotlessCheck`, 테스트 제외 패키징 build, 하네스 semantic 검사와 `git diff --check`가
  통과했다. 전체 `test`는 232개 중 226개가 통과하고 기존 Testcontainers 6개가 로컬
  Docker daemon 부재로 실패했다. 프런트엔드 STOMP client와 운영 reverse proxy·배포
  상태는 확인하지 않았다.

### 관련 문서

- [ADR-0021](0021-remove-premature-exploration-realtime-implementation.md)
- [백엔드 아키텍처](../architecture/backend.md)
- [백엔드 MVP 상태](../product/mvp.md)
- [제품 논의 필요](../product/open-questions.md)
- [탐험 기능 명세](../product/features/exploration.md)
