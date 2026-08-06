resource "aws_ssm_parameter" "db_username" {
  name  = "${var.name}/db/username"
  type  = "SecureString"
  value = var.db_username
}

resource "aws_ssm_parameter" "db_password" {
  name  = "${var.name}/db/password"
  type  = "SecureString"
  value = var.db_password
}

resource "aws_ssm_parameter" "db_url" {
  name  = "${var.name}/db/url"
  type  = "SecureString"
  value = "jdbc:postgresql://${var.db_host}:${var.db_port}/${var.db_name}"
}
