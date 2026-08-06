output "alb_dns_name" {
  description = "배포된 서비스에 접속할 ALB 기본 주소 (http://<이 값>)"
  value       = module.alb_ecs.alb_dns_name
}

output "ecr_repository_url" {
  value = module.ecr.repository_url
}

output "ecs_cluster_name" {
  value = module.alb_ecs.ecs_cluster_name
}

output "ecs_service_name" {
  value = module.alb_ecs.ecs_service_name
}

output "github_actions_role_arn" {
  description = "GitHub Actions 워크플로의 AWS_ROLE_ARN 시크릿에 넣을 값"
  value       = module.iam.github_actions_role_arn
}

output "rds_endpoint" {
  value = module.rds.endpoint
}
