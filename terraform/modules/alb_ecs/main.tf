data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm"
}

# ALB/ECS 보안 그룹은 RDS 모듈과의 순환 의존을 피하기 위해 루트 모듈에서 만들어
# 이 모듈에 전달받는다 (RDS 보안 그룹이 ECS 보안 그룹 ID를 인바운드 허용 대상으로 참조함).

# --- ALB ---

resource "aws_lb" "this" {
  name               = var.name
  internal           = false
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
  security_groups    = [var.alb_security_group_id]
}

resource "aws_lb_target_group" "this" {
  name        = var.name
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = var.health_check_path
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
    timeout             = 5
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }
}

# --- 로그 ---

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.name}"
  retention_in_days = var.log_retention_days
}

# --- IAM: 태스크 실행 Role / 태스크 Role ---

data "aws_iam_policy_document" "ecs_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    effect  = "Allow"
    actions = ["ssm:GetParameters"]
    resources = [
      var.db_url_parameter_arn,
      var.db_username_parameter_arn,
      var.db_password_parameter_arn,
    ]
  }

  statement {
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_alias.ssm.target_key_arn]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "${var.name}-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

resource "aws_iam_role" "task" {
  name               = "${var.name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

# --- ECS 클러스터 / 태스크 정의 / 서비스 ---

resource "aws_ecs_cluster" "this" {
  name = var.name
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.cpu)
  memory                   = tostring(var.memory)
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = var.name
      image     = var.image_uri
      essential = true
      portMappings = [
        { containerPort = var.container_port, protocol = "tcp" }
      ]
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = var.spring_profiles_active }
      ]
      secrets = [
        { name = "DB_URL", valueFrom = var.db_url_parameter_arn },
        { name = "DB_USERNAME", valueFrom = var.db_username_parameter_arn },
        { name = "DB_PASSWORD", valueFrom = var.db_password_parameter_arn },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.this.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "this" {
  name            = var.name
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [var.ecs_security_group_id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.this.arn
    container_name   = var.name
    container_port   = var.container_port
  }

  health_check_grace_period_seconds = 90

  depends_on = [aws_lb_listener.http]
}
