---
status: accepted
decision-date: 2026-08-12
recorded-date: 2026-08-12
---

# ADR-0012 팀 탐험 실시간 채널은 Socket.IO(netty-socketio)로 구현하고 최소 인증 토큰을 도입한다

팀 탐험 지도(4.3)의 실시간 이벤트(방문 인증 전파, 팀원 진행상태, 위치 공유)는
프런트가 이미 `socket.io-client`로 구현을 시작했으므로 백엔드도 Socket.IO
프로토콜로 맞춘다. Java에는 공식 Socket.IO 서버가 없어 서드파티
`com.corundumstudio.socketio:netty-socketio`를 도입한다. 이 실시간 계층은
인증 토큰 없이는 검증할 수 없으므로, 이번 결정에 최소 opaque 토큰 인증
도입도 함께 포함한다.

### 배경

`docs/product/open-questions.md`의 4.1.1·4.2.2·4.3.1·4.3.2·4.3.3 항목이
모두 `[백엔드 확인]` 상태였고, `docs/product/mvp.md`는 3.3.1(코스 확정)부터
4.x(팀 탐험) 대부분을 `미구현`으로 표시하고 있었다. 인증 토큰 체계 자체도
전혀 없었다(`UserLoginResponseDto`에 토큰 없음). 프런트가 "팀 탐험 실시간
통신(Socket.IO) 백엔드 확인 요청" 문서와 `types/socket.ts` 계약안을 전달하며
확정을 요청했다.

최초 검토에서는 `docs/architecture/backend.md`와
`docs/product/features/exploration.md`가 실시간 계층을 "WebSocket"으로만
언급하고 `build.gradle`에 `spring-boot-starter-websocket`이 이미 있다는
근거로 네이티브 Spring WebSocket(STOMP)을 채택했었다. 이후 사용자가 "프론트에서
구현하려는 방식으로 유지"하도록 방침을 번복해 Socket.IO로 확정했다.

### 결정

- 실시간 서버는 `com.corundumstudio.socketio:netty-socketio:2.0.13`
  (Socket.IO 프로토콜 v1~v4 지원, Apache-2.0)을 사용한다. Spring의
  서블릿 컨테이너와 무관하게 자체 Netty 서버를 별도 포트(`socketio.port`,
  기본 9092)에 띄운다.
- 인증: 로그인·회원가입 시 발급하는 opaque 토큰(`auth_tokens` 테이블,
  UUID, 만료 30일, DB 기반 — Redis 불필요·ADR-0006과 일치)을 HTTP API는
  `Authorization: Bearer <token>` 헤더로, 소켓은 handshake 쿼리
  파라미터(`?token=...`)로 전달한다. netty-socketio의
  `AuthorizationListener`가 HTTP 핸드셰이크 레벨에서만 동작하고
  socket.io-client의 `auth: {}` 옵션 payload(엔진 연결 후 CONNECT 패킷
  body)는 읽지 못하기 때문에, **`auth: {}` 옵션이 아니라 `query: { token }`
  형태로 프런트가 토큰을 전달**해야 한다.
- room 기준은 `explorationId`(`exploration:{id}`). Course-Exploration은
  ADR-0004에 따라 생명주기 전체에서 1:1이지만, 소켓 계층은 살아있는 실행
  단위인 Exploration을 직접 가리키는 편이 명확하다.
- payload는 camelCase, `visitedAt`은 epoch milliseconds(`long`)로 통일한다.
- 서버는 소켓 payload에 실려 온 `userId`를 신뢰하지 않고 인증된 handshake
  로 식별한 사용자만 사용한다(HTTP API와 동일한 원칙).
- 이벤트 카탈로그
  - Server→Client: `visit:confirmed`, `member:progress`, `member:location`,
    `member:joined`, `exploration:state`(재연결/입장 시 스냅샷)
  - Client→Server: `exploration:join`, `exploration:leave`,
    `location:update`, `location:optIn`

### 검토한 대안

- 네이티브 Spring WebSocket/STOMP: 기존 문서·의존성과 일치하고 서드파티
  라이브러리가 필요 없어 더 단순했지만, 프런트가 이미 `socket.io-client`로
  구현을 시작한 상태라 사용자가 명시적으로 거부했다.
- netty-socketio의 최신 포크(`socketio4j`, Spring Boot starter 제공)는
  원본(`mrniko/netty-socketio`)이 여전히 활발히 유지되고 있어(2025-03
  최신 릴리스) 채택하지 않았다.

### 영향

- Java 백엔드가 Spring 생태계 밖의 서드파티 실시간 프레임워크에 의존하게
  된다. Spring Security 필터 체인을 거치지 않으므로 인증은
  `AuthorizationListener`에서 별도로 구현해야 한다.
- 별도 포트를 여는 배포 구조라 ALB 리스너·보안그룹에 포트 노출이
  추가로 필요하다 — 운영 배포 문서(`docs/operations/deployment.md`)
  갱신이 후속 과제로 남는다.
- 인증 토큰이 이번에 처음 생겨서 `SecurityConfig`의 `anyRequest().denyAll()`을
  `anyRequest().authenticated()`로 전환했다. 이 전환 자체가 큰 보안 정책
  변경이다.

### 미해결

- `member:left` 브로드캐스트: "탐험 지도 이탈"(Participant→LEFT) REST가
  이번 범위에 없어 트리거 지점이 없다. 후속 작업으로 남긴다.
- `OWNER` 조기 완료(모든 CoursePlace 방문 전 수동 완료)는 이번 범위에
  포함하지 않았다.
- 토큰 만료·로그아웃·재발급(6.1.5 세션 만료)은 다루지 않았다 — 30일
  고정 만료만 있다.
- 좌표 정밀도 마이그레이션(V5) 이후 기존에 `numeric(38,2)`로 저장된
  place 좌표 값 자체의 정밀도는 소급 보정되지 않는다(스키마만 변경).

### 관련 문서

- [탐험 기능 명세](../product/features/exploration.md)
- [백엔드 아키텍처](../architecture/backend.md)
- [ADR-0004](0004-course-exploration-lifecycle.md)
- [ADR-0006](0006-no-distributed-state-or-auto-retry.md)
- [260812-01 팀 탐험 실시간 백엔드 계획](../../.dev/plan/260812-01-exploration-realtime-plan.md)

### 영향 대상

- 변경 유형: `product`, `api`, `data`, `architecture`, `security`
- 수정한 문서·코드: 이 ADR, `docs/product/open-questions.md`,
  `docs/product/mvp.md`, `docs/architecture/backend.md`, `auth`·`course`·
  `exploration`·`visit`·`place` 도메인 코드와 테스트, `V4`·`V5` migration
- 확인했지만 변경하지 않은 대상: `docs/product/features/exploration.md`
  (문구 자체는 유지, WebSocket이라는 표현은 실제로는 Socket.IO 구현을
  가리키는 것으로 재해석)
- 미해결 항목: 위 "미해결" 절 참고. `docs/operations/deployment.md`의
  Socket.IO 포트 노출 반영은 후속 작업.

### 변경 영향 검사

- 검사: 관련 문서·코드 대조, `./gradlew test` 전체 통과(로컬)
- 근거: 2026-08-12 기준. `spotlessCheck`는 이 머신의 google-java-format/JDK
  툴체인 환경 문제로 이번 변경과 무관하게 실행하지 못했다(별도 기록).
