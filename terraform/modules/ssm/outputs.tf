output "parameter_arns" {
  description = "ECS 태스크 실행 Role에 읽기 권한을 부여할 파라미터 ARN 목록"
  value = [
    aws_ssm_parameter.db_username.arn,
    aws_ssm_parameter.db_password.arn,
    aws_ssm_parameter.db_url.arn,
  ]
}

output "db_username_parameter_arn" {
  value = aws_ssm_parameter.db_username.arn
}

output "db_password_parameter_arn" {
  value = aws_ssm_parameter.db_password.arn
}

output "db_url_parameter_arn" {
  value = aws_ssm_parameter.db_url.arn
}
