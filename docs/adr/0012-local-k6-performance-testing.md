---
status: accepted
decision-date: 2026-08-13
recorded-date: 2026-08-13
---

# ADR-0012 성능 테스트는 격리된 로컬 k6 환경에서 수동 실행한다

API 성능 회귀를 반복 측정할 표준 도구로 k6를 채택하고, 공식 Docker 이미지와 기존
애플리케이션 Dockerfile·운영 프로필을 재사용한다. 애플리케이션·PostgreSQL·k6는 개발
환경과 분리된 Docker Compose 프로젝트에서 실행하며, 합성 데이터와 로컬 주소만
허용한다.

JMeter는 풍부한 GUI가 있지만 테스트 정의의 코드 검토와 컨테이너 기반 실행이 더
무겁고, nGrinder는 분산 실행에 유리하지만 컨트롤러·에이전트 운영이 현재 로컬 수동
범위에 비해 크다. 호스트에 k6를 설치하는 방식은 개발자별 버전 차이를 만들기 때문에
공식 `grafana/k6:2.2.0` 이미지를 고정한다.

이 환경은 동일한 로컬 조건의 회귀를 찾기 위한 기준이며 ECS·RDS 운영 용량을 복제하거나
운영 TPS·SLO를 보증하지 않는다. 성능 실행은 CI와 배포 관문에 넣지 않고 사용자가
PowerShell 진입점으로만 시작한다. 애플리케이션 0.5 CPU·1GiB, PostgreSQL 1 CPU·1GiB,
k6 1 CPU·512MiB를 고정된 로컬 비교 조건으로 사용한다.

### 영향 대상

- 변경한 대상: 성능 전용 Docker Compose, k6 health·회원가입 smoke 시나리오,
  PowerShell 실행기와 행동 시험, 성능 테스트 안내, README와 지식 베이스 색인
- 확인했지만 변경하지 않음: 회원가입 API·비즈니스 로직·DB 스키마, 개발용 Docker
  Compose와 DB 볼륨, `application-prod.yml`, CI·PR·배포 workflow, `CONTEXT.md`
- 확인하지 못함: 서로 다른 개발자 머신 사이의 측정 편차와 AWS 운영 용량
- 미해결: load·stress·spike 결과 수집은 #28, 3회 load 기준선은 #29에서 다룬다.

### 변경 영향 검사

- 검사: `change-impact-review` 통과
- 근거: ADR·실행 문서·Docker Compose·PowerShell·k6 스크립트와 회원가입 API 계약을
  대조했다. 실행기의 로컬 대상 거부와 정리 행동 시험, Compose 구성 검사, k6
  스크립트 검사, 전체 테스트·Spotless·빌드와 하네스 검사가 통과했다.
