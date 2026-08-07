# AWS 배포·운영 절차

이 문서는 Beyond May Be 백엔드의 운영 배포 trigger, 상태 확인과 복구 절차의 정본이다.
배포 구조와 승인 결정은
[ADR-0011](../adr/0011-aws-main-continuous-deployment.md), 인프라 생성·변경 절차는
[Terraform 안내](../../terraform/README.md)를 따른다.

## 운영 구성

- 인터넷 요청은 AWS ALB를 거쳐 ECS Fargate의 Spring Boot 컨테이너로 전달된다.
- 애플리케이션 데이터는 RDS PostgreSQL에 저장한다.
- ECS task는 SSM Parameter Store에서 DB 연결 설정을 주입받는다.
- GitHub Actions는 장기 AWS 키 대신 OIDC로 배포 역할을 맡는다.

실제 계정 ID, role ARN, DB 값과 기타 비밀값은 이 문서나 명령 기록에 남기지 않는다.

## 자동 배포와 승인

`.github/workflows/deploy.yml`은 `main` push와 수동 `workflow_dispatch`에서 시작한다.
테스트가 통과하면 이미지를 ECR에 push하고 현재 ECS 서비스를 새 task definition으로
갱신한다. 별도 GitHub `production` Environment 승인 관문은 사용하지 않는다.

PR merge, 직접 push 또는 `workflow_dispatch` 실행 자체가 운영 배포 승인이다. AI가
이 작업을 수행할 때는 실행 직전에 배포 대상, 운영 영향과 아래 복구 방법을 보여주고
다시 확인한다. 저장소 플랜 제한으로 `main` Ruleset과 Branch Protection을 강제하지
못하므로 자동 배포 사실을 PR 검토에서 반드시 확인한다.

## 상태 확인

1. GitHub Actions의 `Deploy` workflow에서 `test`와 `deploy` job 성공을 확인한다.
2. AWS ECS의 `beyond-may-be` cluster와 service에서 rollout 완료와 running task 수를
   확인한다.
3. Terraform의 `alb_dns_name`에 `/actuator/health`를 붙인 Health URL이 HTTP 200과
   `UP`을 반환하는지 확인한다.
4. 오류가 있으면 ECS service event와 CloudWatch log group
   `/ecs/beyond-may-be`를 확인하되 비밀값을 출력하지 않는다.

확인한 workflow run, commit과 시각을 배포 기록에 남긴다. 실제 배포 상태를 확인하지
않았다면 완료 또는 정상으로 기록하지 않는다.

## 이전 버전 복구

실패한 배포 직전의 ECS task definition revision을 확인한 뒤 AWS Console을 사용하거나
다음 명령의 placeholder를 실제 식별자로 바꿔 서비스를 이전 task definition으로
갱신한다.

```bash
aws ecs update-service \
  --cluster beyond-may-be \
  --service beyond-may-be \
  --task-definition <PREVIOUS_TASK_DEFINITION_ARN> \
  --force-new-deployment
```

복구 실행은 운영 환경 변경이므로 최종 대상, 영향과 선택한 이전 revision을 보여주고
실행 직전에 다시 확인한다. 실행 후 service stability와 Health를 다시 확인한다.

## Terraform 변경

Terraform은 자동 배포 workflow에서 `apply`하지 않는다. `terraform plan` 결과를 사람이
검토한 뒤 별도 승인으로 `apply`하며, `destroy`, RDS 삭제 보호 해제와 데이터 변경은
이 자동 애플리케이션 배포 절차에 포함하지 않는다.
