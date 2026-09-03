---
status: accepted
decision-date: 2026-09-03
recorded-date: 2026-09-03
---

# ADR-0027 방문 사진은 비공개 S3에 저장하고 단기 서명 URL로 제공한다

[ADR-0025](0025-visit-confirmation-and-realtime-propagation.md)는 Visit과 사진 이벤트의
경계를 정했지만 사진 업로드와 객체 저장소 계약은 제외했다. 기능 5.2.1의 선택적 다중
사진을 위해 업로드 권한, 원본 공개 범위, 표시 순서와 실패 보상 경계를 확정한다.

### 결정

- 방문 사진은 인증 `POST /api/v1/visits/{visitId}/photos`의 `multipart/form-data`
  `file` 한 장으로 첨부한다. Visit을 만든 사용자이면서 해당 Participant가 `ACTIVE`인
  경우만 허용하고 완료된 Exploration은 409로 거부한다.
- JPEG·PNG·WebP만 허용한다. 선언한 Content-Type과 파일 시그니처가 일치해야 하며 파일은
  장당 10MB 이하여야 한다. 사진 장수 제한과 메모 필드는 두지 않는다.
- 원본은 전용 비공개 S3 버킷의 `visits/{visitId}/{uuid}` key에 저장한다. 버킷은 모든 공개
  접근과 ACL을 차단하고 Bucket owner 강제, SSE-S3 기본 암호화와 HTTPS 전송을 적용한다.
- ECS 애플리케이션은 정적 AWS 키 없이 Task Role의
  `s3:GetObject`·`s3:PutObject`·`s3:DeleteObject` 권한만 사용하며 대상도 해당 버킷의
  `visits/*`로 제한한다. AWS SDK 기본 자격 증명 체인이 ECS Task Role을 사용한다.
- DB에는 기존 `visit_photos.object_key`와 `display_order`만 저장한다. 응답에는 object key를
  노출하지 않고 기본 1시간 유효한 presigned GET URL과 SDK가 계산한 실제 만료 시각을
  반환한다. 인증 `GET /api/v1/visits?explorationId={explorationId}`도 저장된 key로 매번
  새 URL을 발급하며 해당 탐험의 현재 또는 과거 Participant만 팀 전체 기록을 조회한다.
- 같은 Visit 행을 쓰기 잠금한 뒤 현재 최대 표시 순서에 1을 더한다. 기존
  `(visit_id, display_order)` 유일 제약을 최종 방어선으로 유지하며 충돌은 409로 반환한다.
- 객체 업로드 실패는 기존 Visit을 변경하지 않는다. 객체 업로드 뒤 사진 메타데이터 저장
  또는 응답 URL 생성이 실패하면 객체 삭제를 최선 노력으로 시도하고 삭제하지 못한 객체는
  운영 정리 대상으로 남긴다.
- 기존 VisitPhoto 스키마가 계약을 충족하므로 Flyway migration은 추가하지 않는다.

### 검토한 대안

- S3 객체를 공개하면 영구 URL은 단순하지만 팀 방문 사진의 접근 범위를 버킷 공개 정책에
  맡기게 되므로 사용하지 않는다.
- 이미지 파일을 DB에 저장하면 백업·전송 비용과 DB 부하가 커지므로 메타데이터만
  PostgreSQL에 둔다.
- 애플리케이션에 장기 access key를 주입하면 회전·유출 위험이 생기므로 ECS Task Role을
  사용한다.
- 별도 순서 테이블이나 분산 락은 기존 Visit 행 잠금과 유일 제약으로 충분하므로 추가하지
  않는다.
- 클라이언트가 S3로 직접 업로드하는 presigned PUT은 현재 한 장 10MB 서버 검증 흐름보다
  계약과 보상 처리가 복잡하므로, 실제 업로드 부하가 병목일 때 다시 검토한다.

### 영향 대상

- 변경 유형: `product`, `api`, `data`, `architecture`, `security`
- 변경한 대상: 방문 사진 Controller·DTO·Service·Repository·S3 저장소와 오류 처리,
  multipart 설정, AWS SDK 의존성, S3 버킷·ECS Task Role Terraform, 관련 단위·통합 테스트,
  팀 방문 기록 조회 Controller·DTO·Service·Repository, 여행 기록 기능 명세·백엔드
  MVP·아키텍처·제품 논의·Postman·실행 환경 안내
- 확인했지만 변경하지 않음: Visit·VisitPhoto Entity, Flyway V1·V2, 방문 인증 이벤트와
  `/visits` STOMP payload, 인증 토큰 구조, GitHub Actions 배포 workflow
- 확인하지 못함: 실제 AWS 계정의 S3 업로드·서명 URL 조회, 운영 IAM·버킷 정책 적용 결과,
  프런트엔드 multipart 전송과 만료 URL 재조회 처리
- 미해결: 고아 객체 정리 주기, 직접 presigned PUT 전환 조건
- 복구: 코드·문서·Terraform은 Git으로 복구할 수 있다. 배포 뒤 롤백할 때 S3 버킷의 기존
  객체를 먼저 보존하고 이전 애플리케이션 태스크 정의로 되돌린다. 버킷은 비운다는 명시적
  운영 결정 없이 삭제하지 않는다.

### 변경 영향 검사

- 검사 스킬: `change-impact-review`
- 검사 일자: 2026-09-03
- 검사 결과: `통과`
- 검사 근거: 사진 첨부와 후속 팀 방문 기록 GET을 제품·API·데이터·아키텍처·보안 축으로
  대조했다. GET은 현재·과거 참여자만 허용하고 팀 전체 Visit·주변 Place·사진을 일괄
  조회해 매 요청 새 URL을 발급하며 비공개 식별자를 노출하지 않는다. 코드 명세 검토는
  발견 사항 0건이었고 변경 영향·코드 규칙 재검사에서 찾은 이전 상태 문서와 검사 근거를
  바로잡았다. 전체
  404개 테스트, `spotlessCheck`, 전체 `build`, Postman Collection·saved body JSON 파싱,
  제품 지식·하네스 semantic 검사와 `git diff --check`가 통과했다. 기존 Terraform과
  Flyway는 변경할 필요가 없음을 확인했고 실제 AWS·프런트엔드 연동은 영향 대상에 적은
  대로 확인하지 않았다.

### 관련 문서

- [ADR-0025](0025-visit-confirmation-and-realtime-propagation.md)
- [백엔드 아키텍처](../architecture/backend.md)
- [백엔드 MVP 상태](../product/mvp.md)
- [제품 논의 필요](../product/open-questions.md)
- [여행 기록 기능 명세](../product/features/travel-records.md)
- [Terraform 인프라](../../terraform/README.md)
