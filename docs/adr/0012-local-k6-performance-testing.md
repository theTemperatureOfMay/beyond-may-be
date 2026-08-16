---
status: accepted
decision-date: 2026-08-13
recorded-date: 2026-08-13
last-updated: 2026-08-16
---

# ADR-0012 성능 테스트는 격리된 로컬 k6 환경에서 수동 실행한다

API 성능 회귀를 반복 측정할 표준 도구로 k6를 채택하고, 공식 Docker 이미지와 기존
애플리케이션 Dockerfile을 재사용한다. 애플리케이션·PostgreSQL·k6는 개발 환경과 분리된
Docker Compose 프로젝트에서 실행하며 합성 데이터와 로컬 주소만 허용한다. 운영과 같은
애플리케이션 산출물을 사용하되 로컬 성능 Compose에서만 `prod,performance` 프로필을 함께
활성화한다.

JMeter는 풍부한 GUI가 있지만 테스트 정의의 코드 검토와 컨테이너 기반 실행이 더 무겁고,
nGrinder는 분산 실행에 유리하지만 컨트롤러·에이전트 운영이 현재 로컬 수동 범위에 비해
크다. 호스트에 k6를 설치하는 방식은 개발자별 버전 차이를 만들기 때문에 공식
`grafana/k6:2.2.0` 이미지를 고정한다.

각 실행은 `testid`별 HTML·JSON·콘솔 로그·metadata를 Git에서 제외된 로컬 디렉터리에
남긴다. k6 시계열과 애플리케이션 JVM·process·HTTP·HikariCP 지표는 로컬 Prometheus에
수집하고 저장소에서 provision한 Grafana 대시보드로 함께 조회한다. Prometheus raw TSDB는
PC별 외부 named volume에 보존하지만 Grafana 자체 DB는 영속화하지 않는다. 팀이 공유하는
정본은 시나리오·설정·대시보드 정의와 검토해 승인한 비교 문서이며, 서로 다른 PC의 raw
TSDB는 합치거나 직접 비교하지 않는다.

이 환경은 동일한 로컬 조건의 회귀를 찾기 위한 기준이며 ECS·RDS 운영 용량을 복제하거나
운영 TPS·SLO를 보증하지 않는다. 성능 실행은 CI와 배포 관문에 넣지 않고 사용자가
PowerShell 진입점으로만 시작한다. 애플리케이션 0.5 CPU·1GiB, PostgreSQL 1 CPU·1GiB,
k6 1 CPU·512MiB를 고정된 로컬 비교 조건으로 사용한다.

최초 기준선과 갱신 후보는 같은 RPS의 load를 앱·성능 DB 초기화 뒤 세 번 순차 실행하고
지표별 중앙값을 담은 Git 제외 로컬 Markdown 초안으로 만든다. 실행기는 세 원본 결과를
함께 보존하되 추적되는 기준선 문서를 자동으로 생성하거나 수정하지 않는다. 사용자가 같은
머신·Docker 자원·데이터·k6 버전·부하 조건을 확인하고 승인한 비교만 별도 문서 변경으로
승격한다. baseline은 적절한 RPS를 탐색하지 않으며 사용자가 사전 보정이나 기존 프로필
결과를 검토해 비교할 RPS를 선택한다.

### 영향 대상

- 변경한 대상: 성능 전용 Docker Compose, k6 회원가입 smoke·load·stress·spike 시나리오,
  load 3회 기준선 PowerShell 실행기와 행동 시험, Spring `performance` 프로필과 Prometheus registry,
  Prometheus·Grafana provisioning, 실행·결과 해석 문서, README와 지식 베이스 색인
- 확인했지만 변경하지 않음: 회원가입 API·비즈니스 로직·DB 스키마, 개발용 Docker Compose와
  DB 볼륨, `prod` 단독 설정과 운영 ECS, CI·PR·배포 workflow, `CONTEXT.md`, 목표 백엔드
  아키텍처
- 확인하지 못함: 서로 다른 개발자 머신 사이의 측정 편차와 AWS 운영 용량
- 미해결: 없음.

### 검사 근거

- PowerShell 공개 CLI 행동 시험과 Spring `test`·`prod`·`performance` HTTP 보안
  통합 시험을 포함한 전체 테스트, Spotless와 build가 통과했다.
- PowerShell 공개 CLI 행동 시험에서 baseline 필수 RPS, load 3회 순차 실행과 회차별 초기화,
  세 원본 결과와 지표별 중앙값 Markdown 생성을 확인했다.
- 고정 k6 이미지의 `inspect`로 smoke 30초, load 10분, stress 15분, spike 3분,
  arrival-rate stage와 최대 100 VU를 확인했다. Prometheus 설정, Grafana provisioning과
  Compose 모델 검사도 통과했다.
- 실제 Docker에서 5 RPS load·stress와 spike를 실행해 프로필별 결과 산출물과 성공 후
  앱·성능 DB 정리를 확인했다. load는 3,000건, stress는 2,699건, spike는 719건을 처리했고
  오류와 dropped iteration은 없었다. 후속 smoke는 28건 완료·2건 누락으로 실패해 진단
  환경과 원본 결과를 보존했다.
- 실제 Docker에서 기능 검증용 5 RPS baseline을 실행해 앱·성능 DB를 초기화한 load 세
  회차가 각각 3,000건을 오류·dropped iteration 없이 처리하고 세 원본과 로컬 Markdown
  초안을 남기는 것을 확인했다. 중앙값은 처리량 4.982 req/s, p50 10.756ms, p95 19.009ms,
  p99 37.248ms였다. 이 검증의 범위는 3회 실행·초기화·원본·중앙값 생성이며 Git metadata
  완전성은 아래 공개 CLI 회귀 시험을 근거로 한다. 이 결과는 승인된 공식 기준선으로
  승격하지 않았다.
- 공개 CLI 회귀 시험에서 metadata와 중앙값 초안에 Git commit·boolean dirty 상태가
  기록되고, Git 상태를 수집할 수 없으면 기준선 초안을 만들지 않는 것을 확인했다.
- Prometheus와 Grafana를 정상 중지·재시작한 뒤에도 이전 실행의 `testid`와 k6·애플리케이션
  시계열을 조회해 외부 Prometheus named volume의 보존을 확인했다.
- 하네스 지식 베이스·semantic 검사가 통과했다.
- `change-impact-review`로 아키텍처·보안·하네스 영향 집합을 대조했다. 운영 배포,
  개발 DB, 제품 API·데이터 계약, 목표 백엔드 구조와 도메인 용어는 변경하지 않았고,
  실제 실행 결과는 로컬 산출물로 분리했다.
