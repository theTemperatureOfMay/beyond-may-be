# 로컬 성능 테스트

이 문서는 격리된 Docker 환경에서 회원가입 k6 성능 테스트와 로컬 Prometheus·Grafana를
수동 실행하고, load 3회 기준선 초안을 포함한 재검토 가능한 결과를 남기는 방법을 설명한다.

## 목적과 한계

- 같은 개발자 PC와 같은 Docker 자원·버전·데이터 조건에서 API 성능 회귀를 찾는다.
- 개발 DB와 분리된 PostgreSQL에 합성 회원가입 데이터만 저장한다.
- AWS ECS·RDS를 복제하지 않으며 운영 TPS, SLO 또는 용량을 보증하지 않는다.
- 다른 PC의 수치와 raw Prometheus metrics를 합치거나 직접 비교하지 않는다.
- CI, PR 검사와 배포 workflow에서는 실행하지 않는다.

## 사전 조건

- Docker Desktop과 Docker Compose를 실행할 수 있어야 한다.
- Windows PowerShell 5.1 이상에서 저장소 루트를 현재 디렉터리로 사용한다.
- 호스트에 k6, Prometheus 또는 Grafana를 설치할 필요가 없다.
- 기준선을 측정할 때는 불필요한 호스트 프로그램을 종료하고 세 회차 동안 같은 조건을 유지한다.
- 처음 실행하면 고정된 공식 이미지 `grafana/k6:2.2.0`, `prom/prometheus:v3.13.2`,
  `grafana/grafana:13.1.3`을 내려받는다.

## 프로필 실행

`smoke`는 고정 1 RPS이므로 RPS를 입력하지 않는다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\run.ps1 -Profile smoke
```

`load`, `stress`, `spike`, `baseline`은 1 이상 100 이하의 목표 RPS를 반드시 입력한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\run.ps1 -Profile load -Rps 20
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\run.ps1 -Profile stress -Rps 20
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\run.ps1 -Profile spike -Rps 20
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\run.ps1 -Profile baseline -Rps 20
```

| 프로필 | 측정 부하 | 측정 시간 | 실패 판정 |
|---|---|---:|---|
| `smoke` | 1 RPS 고정 | 30초 | 응답 계약 오류 또는 dropped iteration |
| `load` | 입력 RPS 고정 | 10분 | 응답 계약 오류 또는 dropped iteration |
| `stress` | 20%→40%→60%→80%→100%로 즉시 전환해 각 비율을 3분 유지 | 15분 | 오류를 이유로 조기 종료하지 않음 |
| `spike` | 10%→100%→10%로 즉시 전환해 각 비율을 1분 유지 | 3분 | 오류를 이유로 조기 종료하지 않음 |
| `baseline` | 같은 RPS의 load를 앱·성능 DB 초기화 후 3회 순차 실행 | 회차별 30초 워밍업 + 10분 측정 | 한 회차라도 load 기준을 위반하면 중단 |

비율 RPS는 올림하고 최솟값을 1로 둔다. 모든 실행은 arrival-rate 방식이며 최대 VU는
100이다. load·stress·spike는 측정 전에 회원가입을 1 RPS로 30초 워밍업한다. 워밍업은
별도 k6 실행이므로 측정 JSON·HTML·콘솔 로그와 Prometheus remote write에 포함되지 않는다.

`baseline`은 각 회차 전에 앱과 성능 DB를 초기화하고 load를 동시에 실행하지 않는다. 세
회차가 모두 성공해야 원본 `summary.json`의 요청 수·처리량·오류율·checks 성공률·dropped
iterations·p50·p95·p99에서 각각 가운데 값을 선택해 Markdown 초안을 만든다.
적절한 RPS를 탐색하는 명령은 아니므로 사용자가 사전 보정이나 기존 load·stress 결과를
검토해 비교할 RPS를 선택해야 한다. 생성물은 해당 API·RPS 조건의 로컬 기준선 후보다.

## 주소와 데이터 안전 경계

기본 대상은 성능 Compose 내부의 `http://app:8080`이다. 실행기는 정확한 내부 app 주소와
Compose가 로컬에 노출하는 루프백 `18080` 포트만 허용한다. 다른 포트나
경로·인증정보·쿼리·프래그먼트가 있는 주소는 Docker를 시작하기 전에 거부하므로 개발 DB를
사용하는 앱과 운영 주소는 대상이 될 수 없다.

같은 성능 앱을 루프백 경계로 확인해야 할 때만 고정 포트 `18080`을 지정한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\run.ps1 -Profile smoke -TargetBaseUrl http://127.0.0.1:18080
```

실행할 때마다 이전 성능 DB 볼륨을 제거하고 합성 DB를 새로 만든다. 개발용
`postgres_data`, 운영 RDS와 실제 사용자 데이터는 조회하거나 변경하지 않는다.

로컬 비교 조건은 다음 자원 제한으로 고정한다. 운영 ECS·RDS 사양을 복제한 값은 아니다.

| 서비스 | CPU | 메모리 |
|---|---:|---:|
| 애플리케이션 | 0.5 | 1GiB |
| PostgreSQL | 1 | 1GiB |
| k6 | 1 | 512MiB |

## 결과 파일

실행별 원본 결과는 Git에서 제외된 `performance-results/{testid}/`에 남는다.

| 파일 | 내용 |
|---|---|
| `metadata.json` | Git SHA·dirty 여부, profile·RPS·stage·UTC 시간, Docker 버전, 자원 제한과 image 버전 |
| `summary.json` | 처리량·오류율·checks·dropped iterations·p50·p95·p99와 k6 원본 요약 |
| `report.html` | 외부 자원 없이 열 수 있는 실행 요약 |
| `console.log` | 측정 k6 콘솔 출력 |
| `containers-before.json`, `containers-after.json` | 측정 전후 Compose 상태 |
| `observations.md` | 오류 증가 구간·회복 여부·자원 상관관계를 기록하는 메모 |

`baseline`은 위 원본 디렉터리 세 개를 그대로 보존하고
`performance-results/{baseline-id}/baseline.md`를 추가한다. 초안에는 세 실행의 링크와
조건·결과, 지표별 중앙값, Git commit·dirty 상태, Docker Server·자원 조건과 고정 k6
image 버전이 포함된다.

결과에는 호스트명, Windows 사용자명, 실제 개인정보와 실제 자격 증명을 수집하지 않는다.
raw 결과를 자동으로 Git에 추가하지 않는다. 포트폴리오용 전후 비교는 한 PC에서 같은 Git
상태·Docker 자원·image 버전·RPS로 다시 실행하고, 검토한 중앙값·Git SHA·선택 그래프와
해석만 사용자 승인 후 별도 문서 변경으로 승격한다. 실행기는 로컬 초안과 원본만 만들며
추적되는 기준선 문서를 자동으로 생성하거나 수정하지 않는다.

## Grafana와 Prometheus

성공한 실행 뒤에도 관측 서비스는 실행 상태로 남는다.

- Grafana: <http://127.0.0.1:13000/d/k6-app-performance>
- Prometheus: <http://127.0.0.1:19090>

Grafana 대시보드에서 `testid`를 선택하면 k6 처리량·오류·checks·p50·p95·p99·dropped
iterations와 앱 CPU·JVM memory·HTTP·HikariCP를 같은 시간축에서 확인할 수 있다. 기본
시간 범위는 최근 1시간이다. 이전 실행은 해당 `metadata.json`의
`measurementStartedAtUtc`·`measurementEndedAtUtc`를 보고 Grafana 시간 선택기를 맞춘다.
Spring `/actuator/prometheus`는 `performance` 프로필을 명시적으로 활성화한 경우에만
노출·허용된다. 공식 로컬 Compose는 `prod,performance` 조합을 사용하며, `prod` 단독과
운영 ECS에는 노출되지 않는다.

Prometheus raw TSDB는 외부 named volume
`beyond-may-be-performance-prometheus-data`에 90일 또는 2GiB 중 먼저 도달할 때까지
보존된다. 일반 앱·DB 정리와 컨테이너 stop·재시작으로 삭제되지 않는다. Grafana 자체 DB는
영속화하지 않으며 datasource와 대시보드는 저장소 파일에서 다시 provision한다.

관측 서비스만 중지하거나 다시 시작할 수 있다.

```powershell
docker compose -p beyond-may-be-performance -f .\docker-compose.performance.yml stop prometheus grafana
docker compose -p beyond-may-be-performance -f .\docker-compose.performance.yml up -d prometheus grafana
```

PC 교체, Docker Desktop 초기화, retention 만료 또는 명시적 volume 삭제 시 raw TSDB는
사라질 수 있다. Docker Hub 계정이나 다른 Windows 계정·PC로 자동 동기화되지 않는다.

> **데이터 손실 경고:** 다음 명령은 보존한 모든 로컬 성능 시계열을 영구 삭제한다.
> 필요한 결과를 HTML·JSON·metadata 또는 승인된 비교 문서로 남긴 뒤, 사용자가 삭제를
> 명시적으로 결정한 경우에만 실행한다.

```powershell
docker compose -p beyond-may-be-performance -f .\docker-compose.performance.yml rm -s -f prometheus
docker volume rm beyond-may-be-performance-prometheus-data
```

## 성공 정리와 실패 진단

성공하면 앱·PostgreSQL과 성능 DB 볼륨만 자동 제거하고 Prometheus·Grafana와 결과 파일은
남긴다. 실패하거나 중단하면 진단을 위해 앱과 성능 DB도 보존하고 실행기가 정리 명령을
출력한다.

`baseline` 도중 한 회차가 실패하면 다음 회차와 중앙값 초안을 만들지 않는다. 앞선 원본
결과는 보존하고 실패 회차의 앱과 성능 DB는 진단할 수 있게 남긴다.

```powershell
docker compose -p beyond-may-be-performance -f .\docker-compose.performance.yml ps -a
docker compose -p beyond-may-be-performance -f .\docker-compose.performance.yml logs app postgres prometheus grafana
```

진단 후 합성 DB 데이터를 버려도 되는지 확인하고 앱·DB만 정리한다.

```powershell
docker compose -p beyond-may-be-performance -f .\docker-compose.performance.yml rm -s -f app postgres
docker volume rm beyond-may-be-performance-postgres-data
```

## 결과 해석

- load는 유효 응답이나 dropped iteration이 하나라도 실패하면 유효한 기준 실행이 아니다.
- baseline 중앙값도 세 load가 모두 유효하고 같은 머신·Docker 자원·데이터·k6 버전·RPS에서
  실행된 경우에만 비교 기준으로 사용한다.
- stress·spike는 오류 증가 시작 RPS와 부하 감소 뒤 처리량·오류·지연·자원이 회복하는지 본다.
- 실행 시작부터 dropped iteration이 있으면 앱 병목으로 단정하지 않고 먼저 k6 VU 포화와
  로컬 부하 생성기 자원 한계를 확인한다.
- 로컬 수치는 운영 성능 목표가 아니다. 응답시간 합격선과 회귀 허용 범위는 같은 조건의
  안정된 기준선을 확인한 뒤 결정한다.
