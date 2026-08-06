# Terraform 인프라

`beyond-may-be` 백엔드를 AWS(ECS Fargate + RDS PostgreSQL + ALB)에 배포하기 위한
Terraform 구성이다. 설계는 [`.dev/specs/260806-01-aws-deploy-infra-design.md`](../.dev/specs/260806-01-aws-deploy-infra-design.md)를 따른다.

## 사전 준비 (사람이 직접)

1. AWS 계정과 결제 알림을 준비한다.
2. AWS CLI 자격 증명을 로컬에 구성한다 (`aws configure` 또는 SSO).
3. [Terraform CLI](https://developer.hashicorp.com/terraform/install)를 설치한다 (1.6 이상).

## Terraform state 백엔드 부트스트랩 (최초 1회, 수동)

이 저장소는 `backend.tf`에 S3 backend를 선언만 해두고 실제 버킷/테이블 이름은 넣지
않았다. 계정마다 값이 다르고, Terraform 자신도 state를 저장할 곳이 먼저 있어야 하기
때문이다. 아래를 AWS CLI로 최초 1회 직접 실행한다 (버킷 이름은 전역적으로 유일해야
하므로 예시를 그대로 쓰지 말고 바꿔서 사용한다).

```bash
aws s3api create-bucket \
  --bucket beyond-may-be-tfstate-<고유 접미사> \
  --region ap-northeast-2 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2

aws s3api put-bucket-versioning \
  --bucket beyond-may-be-tfstate-<고유 접미사> \
  --versioning-configuration Status=Enabled

aws dynamodb create-table \
  --table-name beyond-may-be-tfstate-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region ap-northeast-2
```

`terraform/backend.hcl` 파일을 만들어 (Git에 커밋하지 않는다) 실제 값을 채운다.

```hcl
bucket         = "beyond-may-be-tfstate-<고유 접미사>"
key            = "prod/terraform.tfstate"
region         = "ap-northeast-2"
dynamodb_table = "beyond-may-be-tfstate-lock"
```

## 초기화와 적용

```bash
cd terraform
terraform init -backend-config=backend.hcl
terraform plan
```

**`plan` 결과를 사람이 직접 검토한 뒤에만 `apply`한다.** 이 저장소의 CI는 인프라를
자동으로 `apply`하지 않는다.

```bash
terraform apply
```

적용 후 `alb_dns_name` 출력값으로 서비스에 접속하고, `github_actions_role_arn` 출력값을
GitHub 저장소 Settings → Secrets and variables → Actions에 `AWS_ROLE_ARN`으로
등록한다 (`.github/workflows/deploy.yml`이 이 값을 사용한다).

## 주의

- `module.rds`는 `deletion_protection = true`로 생성된다. 리소스를 지우려면 먼저
  이 값을 `false`로 바꿔 `apply`한 뒤 `terraform destroy`를 실행해야 한다.
- 첫 `apply`는 `image_tag = "latest"` 기준으로 ECS 태스크 정의를 만든다. 실제 이미지가
  아직 ECR에 없다면 첫 서비스 기동은 실패한 채로 있다가, `deploy.yml`이 처음으로
  이미지를 푸시하고 서비스를 갱신하면 정상화된다.
