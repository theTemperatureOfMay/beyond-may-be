---
status: accepted
decision-date: 2026-08-07
recorded-date: 2026-08-07
---

# ADR-0011 AWS 운영 환경은 main 변경 후 자동 배포한다

백엔드는 Terraform으로 관리하는 AWS ALB·ECS Fargate·RDS PostgreSQL에 배포하고,
GitHub Actions는 OIDC로 단기 AWS 권한을 얻으며 애플리케이션 비밀값은 SSM Parameter
Store에서 주입한다. 비용과 운영 복잡도를 줄이기 위해 NAT Gateway 없이 구성한다.

`main` push와 수동 `workflow_dispatch`는 테스트 통과 후 현재 ECS 서비스를 자동으로
갱신한다. 별도의 GitHub `production` Environment 승인 관문은 두지 않으며, PR merge,
직접 push 또는 `workflow_dispatch`처럼 `main`이나 운영 배포를 시작하는 행위 자체를
운영 배포 승인으로 본다. AI가 이 작업을 수행할 때는 실행 직전에 운영 배포 대상,
영향과 복구 방법을 제시하고 다시 확인한다.

### 영향 대상

- 변경한 대상: `.github/workflows/deploy.yml`, `terraform/`, `Dockerfile`,
  `application-prod.yml`, Flyway baseline migration, `AGENTS.md`, 안전 정책,
  변경 영향 지도, 하네스 완료 기준과 검증기, 프로젝트 README,
  백엔드 아키텍처, 배포·운영 문서, 데모 Runbook, `terraform/README.md`
- 확인했지만 변경하지 않음: 제품 기능 명세와 API 계약
- 확인한 운영 결과: 2026-08-07 `main` push에서 AWS OIDC 인증·ECR 이미지 게시·
  ECS 배포 성공
- 확인하지 못함: 이전 task definition 롤백
- 미해결: 저장소 플랜 제한으로 `main` Ruleset·Branch Protection을 기술적으로 강제하지
  못하는 위험, `workflow_dispatch` 실행 브랜치를 `main`으로 제한하지 않은 상태

### 변경 영향 검사

- 검사: `change-impact-review`
- 결과: 정본과 하네스 갱신 완료, `main` 자동 배포 정상 동작 확인
- 근거: 배포 workflow·Terraform·Flyway 설정, 안전 정책·변경 영향 지도·README·
  백엔드 아키텍처·데모 Runbook·배포 운영 문서·하네스 검증기 대조,
  GitHub Actions 실행 `31157329020`
