variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "project_name" {
  type    = string
  default = "beyond-may-be"
}

variable "github_org" {
  description = "GitHub organization/user"
  type        = string
  default     = "theTemperatureOfMay"
}

variable "github_repo" {
  type    = string
  default = "beyond-may-be"
}

variable "azs" {
  description = "퍼블릭 서브넷을 배치할 가용 영역 (2개)"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.0.0/24", "10.0.1.0/24"]
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "image_tag" {
  description = "최초 배포에 사용할 이미지 태그. 이후 배포는 CD 워크플로가 태스크 정의를 직접 갱신한다."
  type        = string
  default     = "latest"
}

variable "ecs_task_cpu" {
  type    = number
  default = 512
}

variable "ecs_task_memory" {
  type    = number
  default = 1024
}

variable "ecs_desired_count" {
  type    = number
  default = 1
}

variable "rds_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  type    = number
  default = 20
}

variable "rds_backup_retention_period" {
  description = "RDS 자동 백업 보관 일수"
  type        = number
  default     = 3
}

variable "log_retention_days" {
  description = "ECS 컨테이너 로그(CloudWatch Logs) 보관 일수"
  type        = number
  default     = 7
}

variable "db_master_username" {
  type    = string
  default = "beyond_may_be_admin"
}

variable "groq_api_key" {
  description = "Groq API 키 (코스 챗봇/AI 코스 수정 요청). terraform.tfvars 또는 TF_VAR_groq_api_key로 전달한다 — 커밋하지 않는다."
  type        = string
  sensitive   = true
}

variable "tourism_api_key" {
  description = "한국관광공사 TourAPI 키. 아직 코드에서 쓰지 않으며 인프라만 미리 준비해둔다. terraform.tfvars 또는 TF_VAR_tourism_api_key로 전달한다 — 커밋하지 않는다."
  type        = string
  sensitive   = true
}

variable "tourism_api_base_url" {
  description = "한국관광공사 TourAPI(KorService2) base URL"
  type        = string
  default     = "https://apis.data.go.kr/B551011/KorService2"
}

variable "tourism_popularity_api_base_url" {
  description = "한국관광공사 인기순위 API(LocgoHubTarService1) base URL"
  type        = string
  default     = "https://apis.data.go.kr/B551011/LocgoHubTarService1"
}
