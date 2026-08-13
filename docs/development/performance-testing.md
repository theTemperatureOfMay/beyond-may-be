# 로컬 성능 테스트

이 문서는 격리된 Docker 환경에서 k6 성능 테스트를 수동 실행하는 방법을 설명한다.
현재 실행 가능한 프로필은 회원가입 `smoke` 하나다.

## 목적과 한계

- 같은 개발자 머신과 같은 Docker 자원 조건에서 API 성능 회귀를 찾는다.
- 개발 DB와 분리된 PostgreSQL에 합성 회원가입 데이터만 저장한다.
- AWS ECS·RDS를 복제하지 않으며 운영 TPS, SLO 또는 용량을 보증하지 않는다.
- CI, PR 검사와 배포 workflow에서는 실행하지 않는다.

## 사전 조건

- Docker Desktop과 Docker Compose를 실행할 수 있어야 한다.
- Windows PowerShell 5.1 이상에서 저장소 루트를 현재 디렉터리로 사용한다.
- 호스트에 k6를 설치할 필요가 없다. 실행기는 공식 `grafana/k6:2.2.0` 이미지를 사용한다.

## smoke 실행

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\run.ps1 -Profile smoke
```

실행기는 고정된 `beyond-may-be-performance` Compose 프로젝트와
`beyond-may-be-performance-postgres-data` 볼륨만 사용한다. 시작할 때 이전 성능 환경과
전용 볼륨을 초기화한 뒤 애플리케이션과 PostgreSQL을 실행한다. `/actuator/health`가
HTTP 200과 `UP`을 반환하면 health 실행을 끝내고 회원가입을 1 RPS로 30초 측정한다.
health 요청은 smoke 지표에 포함되지 않는다.

회원가입 요청은 10자 이하의 고유 합성 닉네임과 고정된 성향 점수를 사용한다. 모든
응답에서 HTTP 200, `success=true`, 요청 닉네임, `userId`와 `identificationCode`를
확인한다. 응답 검증 실패 또는 dropped iteration이 하나라도 있으면 실행은 실패한다.

## 주소 안전 경계

기본 대상은 성능 Compose 내부의 `http://app:8080`이다. 실행기는 정확한 내부 app 주소와
Compose가 로컬에 노출하는 루프백 `18080` 포트만 허용하며, 다른 포트나
경로·인증정보·쿼리·프래그먼트가 있는 주소는 Docker를 시작하기 전에 거부한다. 따라서
개발 DB를 사용하는 별도 로컬 앱과 운영 주소는 대상이 될 수 없다.

같은 성능 앱을 루프백 경계로 확인해야 할 때만 다음처럼 고정 포트를 지정한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\run.ps1 -Profile smoke -TargetBaseUrl http://127.0.0.1:18080
```

## 자원과 정리

| 서비스 | CPU | 메모리 |
|---|---:|---:|
| 애플리케이션 | 0.5 | 1GiB |
| PostgreSQL | 1 | 1GiB |
| k6 | 1 | 512MiB |

성공하면 성능 컨테이너와 전용 볼륨을 자동으로 제거한다. 실패하거나 실행을 중단하면
진단을 위해 그대로 보존하고 실행기가 정리 명령을 출력한다. 개발용 `postgres_data`
볼륨은 조회하거나 제거하지 않는다.

보존된 상태는 다음 명령으로 확인할 수 있다.

```powershell
docker compose -p beyond-may-be-performance -f .\docker-compose.performance.yml ps
docker compose -p beyond-may-be-performance -f .\docker-compose.performance.yml logs app postgres
```

진단이 끝난 뒤 전용 환경만 정리한다.

```powershell
docker compose -p beyond-may-be-performance -f .\docker-compose.performance.yml down --volumes --remove-orphans
```

현재 smoke 결과는 k6 콘솔에서 확인한다. 실행별 원본 결과와 load·stress·spike 프로필은
#28, 3회 load 기준선은 #29의 범위다.
