# 의존성 관리 운영 기준

이 문서는 `beyond-may-be`의 의존성·GitHub Actions·Docker Compose image 변경을 안전하게
검토하고 유지하는 운영 기준이다. 자동 병합은 사용하지 않으며, 저장소 관리자가 모든
업데이트 Pull Request를 공동으로 검토·병합한다.

## 자동 업데이트 정책

- Dependabot은 Gradle, GitHub Actions, Docker Compose를 각각 매월 검사한다.
- patch·minor 업데이트는 생태계별로 하나의 Pull Request로 묶고, major 업데이트는 별도
  Pull Request로 검토한다.
- Dependabot Alerts가 감지한 보안 업데이트는 월간 묶음에 넣지 않고 즉시·개별 Pull
  Request로 처리한다.
- GitHub Actions는 immutable commit SHA와 사람용 버전 주석을 함께 유지한다. Dependabot이
  두 값을 함께 갱신하는지 확인한다.
- Docker Compose의 PostgreSQL image가 변경되면 Testcontainers의 `POSTGRES_IMAGE`도 같은
  tag·digest로 갱신하고 `PostgreSqlImageConsistencyTest`를 통과시킨다.

## 검토와 대응

- 보안 Pull Request는 24시간 안에 검토하거나 보류 사유 기록을 시작한다.
- 취약점 수정은 현재 major 안의 가장 작은 안전 버전을 우선한다. 이 범위로 해결할 수
  없을 때만 별도 major upgrade Pull Request를 제안한다.
- CI가 실패한 Dependabot Pull Request는 자동으로 닫지 않는다. 호환성 문제, 환경 문제,
  기존 실패를 구분해 원인을 기록하고 후속 조치를 결정한다.
- 새 High·Critical 취약 의존성을 추가하는 Pull Request는 Dependency Review check를
  통과할 수 없으며 `main` 병합 대상에서 제외한다.

## 수정할 수 없는 취약점

호환되는 수정 버전이 없으면 GitHub Issue에 다음 항목을 남긴다.

- GHSA 또는 CVE 식별자와 영향 받는 의존성·버전
- 보류 사유와 현재 완화 조치
- 담당 저장소 관리자
- 재검토 날짜

실제 Issue 생성은 외부 게시이므로 생성 직전에 별도 승인을 받는다.

## GitHub 설정 순서

저장소 관리자는 GitHub의 Security 설정에서 Dependency graph, Dependabot Alerts,
Dependabot security updates를 활성화한다. 관리 기준선 Pull Request의 Dependency Review
workflow가 한 번 성공한 뒤 실제 check context를 확인하고, 그 context를
`main-protection` Ruleset의 required check로 추가한다. 설정 저장과 Ruleset 변경은 공유
GitHub 상태를 바꾸므로 각각 저장 직전에 별도 승인을 받는다.

## 기준선 갱신

의존성을 의도적으로 변경한 Pull Request에서는 다음 명령으로 Gradle lock state를 다시
생성하고 생성된 `gradle.lockfile`만 검토한다. lockfile은 수동 편집하지 않는다.

Windows:

```powershell
.\gradlew.bat dependencies --write-locks
```

macOS/Linux:

```bash
./gradlew dependencies --write-locks
```
