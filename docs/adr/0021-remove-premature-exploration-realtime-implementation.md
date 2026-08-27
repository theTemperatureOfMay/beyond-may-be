---
status: accepted
decision-date: 2026-08-25
recorded-date: 2026-08-25
---

# ADR-0021 팀 탐험 Socket.IO와 선행 REST 구현을 제거하고 재설계 전까지 미구현으로 둔다

기능 4 팀 탐험 지도는 담당자가 이미 설계한 계약으로 다시 구현해야 한다. 기존
Socket.IO와 그 계약에 맞춰 먼저 구현된 팀원 조회·탐험 시작·방문 인증 API는 새 설계의
기준으로 사용하지 않고 제거한다. 이번 결정에서는 대체 WebSocket(STOMP) 계약을 정하거나
구현하지 않는다.

### 결정

- `netty-socketio` 의존성, 별도 서버 설정, 핸들러, 이벤트 DTO와 브로드캐스트 코드를
  제거한다.
- 다음 공개 API와 전용 Service·DTO·Repository 조회·테스트·Postman 요청을 제거한다.
  - `GET /api/v1/explorations/{explorationId}/members`
  - `POST /api/v1/explorations/{explorationId}/start`
  - `POST /api/v1/explorations/{explorationId}/places/{placeId}/visits`
- `course` 패키지의 기능은 유지한다. 특히 공개 코스 미리보기
  `GET /api/v1/courses/{courseId}`와 팀 합류
  `POST /api/v1/courses/{courseId}/join`은 기존 계약대로 보존한다.
- `Exploration`, `ExplorationParticipant`, `Visit`, `VisitPhoto` Entity와 기존 Flyway
  migration·테이블은 삭제하지 않는다. V5의 장소 좌표 `numeric(9,6)` 정밀도도 유지한다.
  운영 데이터 변경이나 DB rollback은 없다.
- `docs/product/features/exploration.md`의 기능 의도는 변경하지 않는다. 실시간 프로토콜,
  STOMP endpoint·destination·payload와 인증 계약은 담당 설계가 확정된 뒤 별도 결정한다.
- 로그인·회원가입과 HTTP API가 사용하는 DB 기반 opaque bearer 토큰은 유지한다.
  Socket.IO handshake 쿼리 토큰 계약만 제거한다.

이 결정은 Socket.IO 채택과 구체 이벤트·소켓 인증 결정을 철회하면서
[ADR-0012](0012-team-exploration-realtime-channel.md)를 대체한다. ADR-0012에서 함께 도입한
HTTP opaque 토큰 인증은 현재 course 합류를 포함한 보호 API가 사용하므로 유지한다.

### 검토한 대안

- 같은 변경에서 Socket.IO를 STOMP로 바로 전환하면 아직 확정되지 않은 계약을 다시 선행
  구현하게 되므로 사용하지 않는다.
- 기능 4와 연관된 모든 코드를 제거하면 기능 3의 공개 코스 미리보기와 사용자가 명시적으로
  보존한 course 합류까지 삭제되므로 사용하지 않는다.
- Visit·Exploration 테이블까지 삭제하면 운영 데이터 손실과 migration rollback 결정이
  필요해지므로 사용하지 않는다.

### 영향 대상

- 변경 유형: `product`, `api`, `architecture`, `security`
- 변경한 대상: Socket.IO 의존성·설정·실시간 코드, exploration의 팀원 조회·시작 코드,
  visit API 계층과 관련 테스트, Postman 원본 Collection, 백엔드 MVP 상태·아키텍처·제품
  논의 문서, ADR-0012 상태
- 확인했지만 변경하지 않음: `course` 패키지와 코스 미리보기·합류 API,
  `docs/product/features/exploration.md`, 사용자 흐름·데모 목표 문서, Entity·Flyway
  migration·기존 DB 데이터, HTTP bearer 인증, place API, README 규칙상 직접 수정하지 않는
  `postman/postman/` 내부 작업 폴더
- 확인하지 못함: 프런트엔드 호출부, Notion 초안 이후의 최종 STOMP 계약, 운영 배포 상태
- 미해결: WebSocket(STOMP) endpoint·destination·payload·재연결·인가 계약과 제거한 REST
  API의 재설계
- 복구: 코드와 문서는 Git으로 복구할 수 있다. DB 변경이 없어 데이터 복구 절차는 없다.

### 변경 영향 검사

- 검사 스킬: `change-impact-review`
- 검사 일자: 2026-08-25
- 검사 결과: `통과`
- 검사 근거: Controller·DTO·Service·Repository·설정·의존성·보안 설정, course·join 흐름,
  Entity·migration, 기능 명세·사용자 흐름·MVP·백엔드 아키텍처·제품 논의·ADR과 Postman
  원본 Collection을 대조했다. 전체 219개 테스트, `spotlessCheck`, 전체 `build`, 하네스
  semantic 검사, Postman JSON 파싱, 제거 경로 검색과 `git diff --check`가 통과했다.
  프런트엔드·Notion 최종 계약과 운영 배포 상태는 확인하지 않았다.

### 관련 문서

- [탐험 기능 명세](../product/features/exploration.md)
- [백엔드 MVP 상태](../product/mvp.md)
- [백엔드 아키텍처](../architecture/backend.md)
- [제품 논의 필요](../product/open-questions.md)
