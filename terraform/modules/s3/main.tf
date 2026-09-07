# 방문 인증 사진 원본 저장용 비공개 버킷. object_key는 DB에만 저장하고 버킷은
# 퍼블릭 접근을 전부 차단한다 (백엔드아키텍처.md: "사진 파일은 객체 저장소,
# DB에는 비공개 object_key와 표시 순서를 저장한다").

resource "aws_s3_bucket" "this" {
  bucket = var.name
}

resource "aws_s3_bucket_public_access_block" "this" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
