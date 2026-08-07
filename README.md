# Beyond May Be Backend

Beyond May Be는 여행 취향이 서로 다른 친구들이 함께 국내 여행을 계획할 수 있도록,
성향 분석부터 장소 선택, 코스 생성·탐험·기록까지 연결하는 서비스다.

이 저장소는 Beyond May Be의 백엔드 API 서버를 관리한다.

## 현재 상태

현재 프로젝트는 Java 21, Spring Boot와 PostgreSQL 기반의 초기 스켈레톤 단계다.
API·DTO·테스트가 있어도 실제 비즈니스 기능이 구현됐다는 의미는 아니며 요청·응답
계약도 변경될 수 있다. 기능별 구현 상태는
[백엔드 MVP 상태](docs/product/mvp.md)에서 확인한다.

## 사전 조건

- 저장소 루트에서 명령을 실행한다.
- JDK 21을 설치한다.
- Docker와 Docker Compose를 실행할 수 있어야 한다.
- Windows에서는 PowerShell 또는 명령 프롬프트를 사용한다.

## 빠른 시작

### 환경 변수

저장소 루트의 `.env.example`을 `.env`로 복사한다.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```bash
cp .env.example .env
```

`.env`에는 로컬 환경의 실제 값을 기록하며 Git에 추가하지 않는다. `.env.example`에는
비밀번호, API 키, 접근 토큰이나 실사용자 정보를 기록하지 않는다.

### Git 훅 설치

새로 clone한 저장소에서는 한 번 다음 명령을 실행한다.

Windows:

```powershell
.\gradlew.bat installGitHooks
```

macOS/Linux:

```bash
./gradlew installGitHooks
```

설치된 pre-commit 훅은 commit 전에 `spotlessCheck`를 실행한다.

커밋 메시지는 [커밋 컨벤션](docs/development/commit-convention.md)을 따른다.

### PostgreSQL 실행

```bash
docker compose up -d postgres
docker compose ps
```

`postgres` 서비스가 `healthy` 상태가 되면 준비가 완료된다. 데이터는
`postgres_data` Docker 볼륨에 유지된다.

### 애플리케이션 실행

Windows:

```powershell
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
./gradlew bootRun
```

Health가 <http://localhost:8080/actuator/health>에서 HTTP 200과 `UP`을 반환하면 기본
실행 검증이 완료된 것이다.

## API 문서

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- 실행 가능한 요청과 saved example: [Postman 사용 안내](postman/README.md)

API 변경 시 Swagger/OpenAPI, 관련 테스트와 Postman 자료를 함께 검토한다.

## 검증

모든 명령은 저장소 루트에서 실행한다. 통합 테스트와 전체 테스트에는 Docker가
필요하다.

| 목적 | Windows | macOS/Linux |
|---|---|---|
| 하네스 semantic 검사 | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\harness\verify-harness.ps1` | `pwsh -File ./scripts/harness/verify-harness.ps1` |
| 하네스 환경 doctor | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\harness\harness-doctor.ps1` | `pwsh -File ./scripts/harness/harness-doctor.ps1` |
| 관련 테스트 | `.\gradlew.bat test --tests "전체.테스트.클래스명"` | `./gradlew test --tests "전체.테스트.클래스명"` |
| 전체 테스트 | `.\gradlew.bat test` | `./gradlew test` |
| JaCoCo 리포트 | `.\gradlew.bat jacocoTestReport` | `./gradlew jacocoTestReport` |
| 코드 품질 검사 | `.\gradlew.bat spotlessCheck` | `./gradlew spotlessCheck` |
| 전체 빌드 | `.\gradlew.bat build` | `./gradlew build` |

검사를 실행하지 못하면 완료로 간주하지 않고 원인과 미실행 검사를 구분해 보고한다.
CI와 `main` 보호 규칙은 [하네스 문서 안내](docs/harness/README.md)에서 확인한다.

## 온보딩과 작업 안내

| 경로 | 용도 |
|---|---|
| `src/main/java` | 애플리케이션 코드 |
| `src/test/java` | 자동화 테스트 |
| `docs/` | 제품·아키텍처·개발 정본 |
| `scripts/harness/` | 하네스 의미 검증 |
| `postman/` | API 요청과 예시 |

제품·아키텍처·개발·데모 문서는 [지식 베이스 안내](docs/index.md)에서 찾는다. AI
에이전트의 작업 절차는 [AGENTS.md](AGENTS.md)를 따른다.

## 주의사항

- `.env`와 실제 비밀값을 출력하거나 커밋하지 않는다.
- 로컬 설정은 Hibernate `ddl-auto=update`를 사용하므로 `DB_URL`을 공유·운영 DB로
  지정하지 않는다.
- DB 스키마·데이터 삭제와 외부 서비스 쓰기는 실행 직전에 명시적인 승인을 받는다.

자세한 보호 영역과 외부 작업 기준은
[AI 에이전트 안전 정책](docs/harness/safety-policy.md)을 따른다.
