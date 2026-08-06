variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "alb_security_group_id" {
  type = string
}

variable "ecs_security_group_id" {
  type = string
}

variable "image_uri" {
  description = "ECR 이미지 URI (태그 포함, 예: 123.dkr.ecr.ap-northeast-2.amazonaws.com/beyond-may-be:latest)"
  type        = string
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "health_check_path" {
  type    = string
  default = "/actuator/health"
}

variable "cpu" {
  description = "Fargate 태스크 CPU 단위"
  type        = number
  default     = 512
}

variable "memory" {
  description = "Fargate 태스크 메모리(MiB)"
  type        = number
  default     = 1024
}

variable "desired_count" {
  type    = number
  default = 1
}

variable "spring_profiles_active" {
  type    = string
  default = "prod"
}

variable "log_retention_days" {
  type    = number
  default = 7
}

variable "aws_region" {
  description = "CloudWatch Logs 설정에 사용할 리전"
  type        = string
}

variable "db_url_parameter_arn" {
  type = string
}

variable "db_username_parameter_arn" {
  type = string
}

variable "db_password_parameter_arn" {
  type = string
}
