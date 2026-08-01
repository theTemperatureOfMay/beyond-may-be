# 하네스 단계별 구축 로드맵

- 상태: 동결된 역사 기록
- 보존 범위: 2026-07-25까지의 하네스 구축·검증 이력
- 결정 일자: 2026-07-22
- 현재 상태 확인 일자: 2026-07-25
- 병합 근거: Pull Request #1 (`bbcf6f7`), Pull Request #3 (`5a9ed83`)
- 기준 문서: [하네스 완성 기준 및 평가표](completion-criteria.md)

이 문서는 2026-07-25까지의 구축 과정과 당시 검증 근거를 보존하기 위해 동결한다.
현재 하네스 정책, 완료 기준과 재검사 조건은 [하네스 완성 기준 및 평가표](completion-criteria.md),
[하네스 문서](README.md), [안전 정책](safety-policy.md)을 따른다. 이 문서에는
새로운 진행 상태나 현재 프로젝트 사실을 추가하지 않으며, 과거 사실 또는 링크의
오류만 필요한 범위에서 바로잡는다.

## 목적

하네스 전체를 한 번에 완성하려 하지 않고, 이후 작업의 판단 근거가 되는 항목부터
세션 단위로 구축한다. 각 세션은 하나의 명확한 목표와 완료 조건을 가지며, 완료된
내용을 확인한 뒤 다음 우선순위로 이동한다.

## 완료된 우선 작업

복구한 Green 검증 기준선을 Git 훅, GitHub Actions와 `main` Ruleset에서 강제하도록
구성했다.
Public repository와 관리 권한을 구성하고 실제 실패·복구 Pull Request로 로컬 commit
차단, 원격 CI 실패, merge 차단과 동일 체크의 Green 전환을 확인했다.

### 포함 범위

1. `theTemperatureOfMay/beyond-may-be` Public repository 초기화와 관리자 권한 구성
2. Ubuntu·Java 21에서 `./gradlew build`를 실행하는 최소 GitHub Actions CI 추가
3. 명시적 Gradle task로 설치하는 Spotless pre-commit 훅 복구
4. `main`의 Pull Request, conversation 해결, 최신 branch와 실제 `build` 체크 강제
5. 의도적 Spotless 위반의 로컬 commit 차단, 원격 실패·merge 차단과 복구 후 Green 검증
6. 실제 설정과 검증 결과를 `README.md`와 이 로드맵에 기록

### 작업 원칙

- `main` 최초 bootstrap push 이후에는 Pull Request 경로만 사용한다.
- required check 이름은 추측하지 않고 실제 CI run의 `build` context를 등록한다.
- 실패 검증용 포맷 위반은 동작을 바꾸지 않고, 원격 실패 확인 직후 정상 포맷으로
  복구한다.
- 관리자 bypass를 두지 않으며 삭제나 force push를 실제로 시도하지 않는다.
- 비밀번호, API 키, 접근 토큰, 개인정보와 실제 환경 변수 값은 출력하거나 기록하지 않는다.

### 이번 세션에서 제외하는 작업

- Claude Code와 Codex 권한 설정 추가
- 배포, 외부 쓰기, MCP와 플러그인 안전 정책 구현
- Claude Code와 Codex 회귀 시험 실행
- 하네스 담당자와 리뷰 절차 확정
- required approval 1명, 커버리지와 보안 검사 강제
- 프런트엔드 저장소, Organization base permission, merge 방식과 secret 변경
- Pull Request #1 merge — 당시 세션에서는 제외했으며 이후 `bbcf6f7`에서 완료

## 완료 조건

다음을 모두 만족하면 현재 우선 작업이 완료된 것으로 본다.

- 새 clone에서 `installGitHooks` task로 pre-commit 훅을 설치할 수 있다.
- Spotless 위반은 일반 commit과 원격 `./gradlew build`에서 모두 차단된다.
- 위반 복구 후 로컬 전체 테스트, Spotless, build와 동일한 원격 `build` 체크가 통과한다.
- `main-protection`이 `main`만 대상으로 PR, conversation 해결, 최신 branch와 실제
  `build` 체크를 강제한다.
- 삭제와 force push가 차단되고 required approval과 bypass는 없다.
- 최종 Pull Request는 Green, Ready for review 상태를 거쳐 `main`에 병합됐다.
- `README.md`와 이 문서가 실제 설정 및 검증 결과와 일치한다.

## 후속 세션 우선순위

하네스 담당 역할, 갱신 조건과 재검사 조건은
[완성 기준의 운영과 유지관리 정책](completion-criteria.md#g6-운영과-유지관리)에서
확정했다.

1. 보호 영역, 외부 콘텐츠, MCP·플러그인과 외부 쓰기 승인 기준을 공통 지침과 상세
   안전 정책으로 문서화했다. Codex·Claude 프로젝트 권한 설정은 제외하고 기술적
   강제가 없다는 남은 위험을 기록했다.
2. Windows PowerShell 5.1에서 실행되는 semantic 검증과 선택적 behavioral 시험
   준비·판정 기반을 추가했다. semantic 검증은 기존 CI `build`에 연결하고
   behavioral 검증은 자동 실행하지 않는다.
3. 사용자가 요청하면 하네스 구축 완료 직전에 Codex 회귀 시험 10개를 실행하고
   결과를 기록한다.
   Claude Code는 공통 규칙 연결만 정적으로 확인하고 행동 회귀 대상으로 삼지 않는다.
4. 완성 기준과 회귀 시험을 통과하면 required approval을 1명으로 전환한다.
5. 팀 협업이 안정되면 커버리지와 보안 검사를 단계적으로 검토한다.

후속 세션을 시작하기 전에는 직전 단계의 완료 조건을 먼저 확인한다.

## 2026-07-22 CI 필수 관문 구축 결과

### 구현 결과

- 빈 Public repository `theTemperatureOfMay/beyond-may-be`를 만들고 기존 로컬 `main`
  기준 이력을 한 번 bootstrap push했다.
- Organization Owner인 `LJYeon12`와 direct Admin인 `chhyejin`만 관리자 범위에 두고
  다른 member나 team에는 이 저장소의 direct access를 추가하지 않았다.
- `CI / build`가 `main` Pull Request, `main` push와 수동 실행에서 Ubuntu·Java 21의
  `./gradlew build`를 실행하도록 구성했다.
- `.githooks/pre-commit`과 `installGitHooks` Gradle task를 추가했다. 훅은
  `spotlessCheck`만 실행하며 자동 수정하지 않는다.
- Active `main-protection` Ruleset에 approvals 0, bypass 없음, PR과 conversation 해결,
  최신 branch, GitHub Actions `build`, 삭제 제한과 force push 차단을 설정했다.

### 검증 결과

- 의도적 Spotless 위반으로 로컬 `spotlessCheck`와 일반 commit이 차단됐고, 원격 첫
  build run `29861594363`도 `spotlessJavaCheck`에서 1분 16초 만에 실패했다.
- 실패 상태의 Ready Pull Request #1에서 `CI / build (pull_request)`가 Required로
  표시되고 merge 버튼이 비활성화되는 것을 확인했다.
- 위반을 복구한 commit `7444425`는 pre-commit 훅을 정상 통과했다. 로컬 전체 테스트를
  `--rerun-tasks`로 실행해 1분 39초에 통과했고 `spotlessCheck`와 전체 build도 통과했다.
- 동일 체크의 run `29862648816`은 `spotlessCheck`, test와 build task를 실제 실행해
  1분 21초에 성공했다. 전체 GitHub Actions job은 1분 28초가 걸렸다.
- Pull Request #1은 당시 Green, Ready for review, 미병합 상태로 유지했으며 이후
  merge commit `bbcf6f7`로 `main`에 병합됐다.

### 남은 작업

- required approval 1명 강제는 G1부터 G6까지의 관문과 Codex 회귀 시험을 모두
  통과해 하네스 구축 완료를 판정한 뒤 적용한다.
- 커버리지와 보안 검사는 현재 필수 관문에 포함하지 않았으며 후속 단계로 유지한다.

## 2026-07-22 검증 기준선 복구 결과

### 구현 결과

- 현재 비-ignore 프로젝트 상태를 기준선 커밋 `dde9f17`로 보존했다.
- 방문 인증 API와 GPS 요청·응답 스켈레톤의 소유권을 `course`에서 `courseplace`로
  이전하고 공개 경로는 유지했다.
- Postman 방문 인증 요청과 saved example을 문자열 식별자, GPS 본문과 고정 응답에
  맞췄다.
- Windows Gradle Wrapper 명령을 PowerShell에서 실행 가능한 `.\gradlew.bat` 형식으로
  정리했다.
- 전체 Java 소스에 Spotless 포맷을 적용했다.

### 검증 결과

- Docker Desktop 28.1.1과 PostgreSQL 컨테이너의 `healthy` 상태를 확인했다.
- 방문 인증 관련 테스트, `SecurityPolicyIntegrationTest`와
  `BeyondMayBeApplicationTests`가 통과했다.
- 전체 테스트는 2분 11초, 전체 빌드는 1분 56초에 통과했고 `spotlessCheck`도
  통과했다.
- 실제 애플리케이션에서 Health HTTP 200과 `UP`, Swagger UI HTTP 200, OpenAPI JSON
  HTTP 200과 방문 인증 경로 1회 노출을 확인했다.
- 방문 인증은 고정 응답 스켈레톤이며 DB 저장, GPS 거리 판정, 인증과 실제 사용자
  식별은 다음 기능 구현 범위로 남아 있다.

## 2026-07-22 이전 세션 구현 및 검증 결과

### 구현 결과

- 루트 `README.md`에 프로젝트 목적, MVP 흐름, 현재 개발 상태, 역할과 권한, 도메인,
  실행·검증 방법, 보호 영역과 문서 연결을 작성했다.
- `docs/product/user-flow.md`와 원본 플로우차트 이미지에 상세 MVP 흐름을 기록했다.
- `.env.example`에 로컬 PostgreSQL용 안전한 예시 값을 추가했다.
- `AGENTS.md`가 프로젝트 지식 원본인 `README.md`를 확인하도록 연결했다.

### 확인 결과

- 문서의 로컬 링크와 플로우차트 이미지 연결을 확인했다.
- 원본 이미지와 저장소 이미지의 SHA-256 해시가 일치했다.
- `.env`는 Git에서 제외되고 `.env.example`은 제외되지 않음을 확인했다.
- Gradle 9.5.1과 JDK 21을 확인했다.
- Docker Compose PostgreSQL이 `healthy` 상태가 됨을 확인했다.
- 애플리케이션은 PostgreSQL 연결까지 성공했지만 기존 Controller의 중복 API 매핑으로
  시작에 실패했다.
- 전체 테스트는 4분 제한 안에 완료되지 않았다.
- Spotless는 기존 Java 파일 53개의 포맷 위반으로 실패했다.
- 전체 빌드는 컴파일과 패키지 조립 후 기존 `spotlessJavaCheck` 위반으로 실패했다.

위 내용은 검증 기준선 복구 전의 이전 세션 결과다. 이번 세션에서 애플리케이션 기동,
Health, Swagger/OpenAPI, 전체 테스트, Spotless와 전체 빌드를 다시 검증해 현재 단계의
완료 조건을 충족했다.
