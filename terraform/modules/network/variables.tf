variable "name" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR 블록"
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs" {
  description = "사용할 가용 영역 목록 (퍼블릭 서브넷을 이 개수만큼 분산 배치)"
  type        = list(string)
}

variable "public_subnet_cidrs" {
  description = "가용 영역별 퍼블릭 서브넷 CIDR 목록 (azs와 같은 순서, 같은 길이)"
  type        = list(string)
}
