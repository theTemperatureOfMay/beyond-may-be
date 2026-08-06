# 계정별로 다른 버킷/테이블 이름을 하드코딩하지 않기 위해 backend 설정을 비워둔다.
# 실제 값은 `terraform init -backend-config=backend.hcl`로 전달한다.
# backend.hcl 작성법은 terraform/README.md를 참고한다 (버킷/테이블은 README의 부트스트랩
# 절차로 최초 1회 직접 만든다).
terraform {
  backend "s3" {}
}
