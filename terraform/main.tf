data "aws_caller_identity" "current" {}

resource "random_password" "db" {
  length  = 24
  special = false
}

module "network" {
  source              = "./modules/network"
  name                = var.project_name
  azs                 = var.azs
  public_subnet_cidrs = var.public_subnet_cidrs
}

# ALB/ECS 보안 그룹은 RDS ↔ ECS 사이의 순환 의존을 피하기 위해 루트에서 만든다.

resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb"
  description = "ALB inbound: HTTP(80) from the internet"
  vpc_id      = module.network.vpc_id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-alb" }
}

resource "aws_security_group" "ecs" {
  name        = "${var.project_name}-ecs"
  description = "ECS task inbound: only from the ALB security group"
  vpc_id      = module.network.vpc_id

  ingress {
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-ecs" }
}

module "ecr" {
  source = "./modules/ecr"
  name   = var.project_name
}

module "rds" {
  source                     = "./modules/rds"
  name                       = var.project_name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.public_subnet_ids
  allowed_security_group_ids = [aws_security_group.ecs.id]
  instance_class             = var.rds_instance_class
  allocated_storage          = var.rds_allocated_storage
  backup_retention_period    = var.rds_backup_retention_period
  master_username            = var.db_master_username
  master_password            = random_password.db.result
}

module "ssm" {
  source      = "./modules/ssm"
  name        = "/${var.project_name}/prod"
  db_username = var.db_master_username
  db_password = random_password.db.result
  db_host     = module.rds.address
  db_port     = module.rds.port
  db_name     = module.rds.db_name
}

module "alb_ecs" {
  source = "./modules/alb_ecs"

  name                  = var.project_name
  vpc_id                = module.network.vpc_id
  public_subnet_ids     = module.network.public_subnet_ids
  alb_security_group_id = aws_security_group.alb.id
  ecs_security_group_id = aws_security_group.ecs.id

  image_uri          = "${module.ecr.repository_url}:${var.image_tag}"
  container_port     = var.container_port
  cpu                = var.ecs_task_cpu
  memory             = var.ecs_task_memory
  desired_count      = var.ecs_desired_count
  aws_region         = var.aws_region
  log_retention_days = var.log_retention_days

  db_url_parameter_arn      = module.ssm.db_url_parameter_arn
  db_username_parameter_arn = module.ssm.db_username_parameter_arn
  db_password_parameter_arn = module.ssm.db_password_parameter_arn
}

module "iam" {
  source = "./modules/iam"

  name        = var.project_name
  github_org  = var.github_org
  github_repo = var.github_repo

  ecr_repository_arn          = module.ecr.repository_arn
  ecs_cluster_arn             = "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:cluster/${module.alb_ecs.ecs_cluster_name}"
  ecs_service_arn             = "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/${module.alb_ecs.ecs_cluster_name}/${module.alb_ecs.ecs_service_name}"
  ecs_task_execution_role_arn = module.alb_ecs.task_execution_role_arn
  ecs_task_role_arn           = module.alb_ecs.task_role_arn

  task_definition_family_arn_pattern = "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task-definition/${module.alb_ecs.task_definition_family}:*"
}
