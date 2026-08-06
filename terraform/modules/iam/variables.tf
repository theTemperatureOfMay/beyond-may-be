variable "name" {
  type = string
}

variable "github_org" {
  description = "GitHub organization/user 이름"
  type        = string
}

variable "github_repo" {
  description = "GitHub 저장소 이름 (org 제외)"
  type        = string
}

variable "allowed_ref" {
  description = "이 Role을 맡을 수 있는 브랜치 ref"
  type        = string
  default     = "refs/heads/main"
}

variable "ecr_repository_arn" {
  type = string
}

variable "ecs_cluster_arn" {
  type = string
}

variable "ecs_service_arn" {
  type = string
}

variable "ecs_task_execution_role_arn" {
  type = string
}

variable "ecs_task_role_arn" {
  type = string
}

variable "task_definition_family_arn_pattern" {
  description = "RegisterTaskDefinition/DescribeTaskDefinition을 제한할 태스크 정의 family ARN 패턴 (예: arn:aws:ecs:REGION:ACCOUNT:task-definition/FAMILY:*)"
  type        = string
}
