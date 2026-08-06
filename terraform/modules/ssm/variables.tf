variable "name" {
  description = "파라미터 이름에 쓸 접두사 (예: /beyond-may-be/prod)"
  type        = string
}

variable "db_username" {
  description = "RDS 마스터 사용자명"
  type        = string
}

variable "db_password" {
  description = "RDS 마스터 비밀번호"
  type        = string
  sensitive   = true
}

variable "db_host" {
  description = "RDS 엔드포인트 호스트"
  type        = string
}

variable "db_port" {
  description = "RDS 포트"
  type        = number
  default     = 5432
}

variable "db_name" {
  description = "데이터베이스 이름"
  type        = string
}
