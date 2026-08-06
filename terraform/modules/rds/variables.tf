variable "name" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "DB 서브넷 그룹에 사용할 서브넷 (최소 2개, 서로 다른 AZ)"
  type        = list(string)
}

variable "allowed_security_group_ids" {
  description = "RDS 인바운드(5432)를 허용할 보안 그룹 목록 (ECS 태스크 보안 그룹)"
  type        = list(string)
}

variable "engine_version" {
  type    = string
  default = "17.10"
}

variable "instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "allocated_storage" {
  type    = number
  default = 20
}

variable "backup_retention_period" {
  description = "자동 백업 보관 일수 (프리티어 백업 스토리지도 20GB까지만 무료)"
  type        = number
  default     = 3
}

variable "db_name" {
  type    = string
  default = "beyond_may_be"
}

variable "master_username" {
  type    = string
  default = "beyond_may_be_admin"
}

variable "master_password" {
  description = "RDS 마스터 비밀번호 (루트 모듈에서 random_password로 생성해 전달)"
  type        = string
  sensitive   = true
}
