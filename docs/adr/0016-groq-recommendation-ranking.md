---
status: accepted
decision-date: 2026-08-21
recorded-date: 2026-08-21
last-updated: 2026-08-21
---

# ADR-0016 Groq 후보 선별과 서버 전체 검증

추천 장소 생성은 서버가 활성 DB 장소의 유형별 최종 할당량과 최대 60곳의 허용 후보를
확정한 뒤, Groq `openai/gpt-oss-20b`가 그 후보 ID만 순서화하도록 한다. AI는 추천에
필수인 정본이 아니므로 strict JSON Schema 응답을 서버가 전부 검증하고, 통신·파싱·계약
검증이 하나라도 실패하면 사용자 ID와 날짜 기반의 안정적인 규칙 결과 전체로 대체한다.

## 결정

- Groq에는 네 성향 점수, 최종 할당량, 일정과 후보의 허용 장소 정보만 보내며 사용자 ID,
  닉네임, 식별코드, 사용자 인증 정보는 보내지 않는다.
- 응답은 순서가 있는 `placeIds` 배열 하나만 허용한다. 전달 후보 포함 여부, 중복, 현재
  DB 존재·활성 상태, 목표 수와 유형별 할당량을 저장 직전에 모두 검증하며 일부 결과는
  보정하지 않는다.
- TourAPI 응답의 신규 장소 적재와 AI 후보 준비는 첫 번째 짧은 트랜잭션에서 처리한다.
  Groq는 DB 트랜잭션과 사용자 행 잠금 밖에서 최대 20초 동안 한 번만 호출한다. 두 번째
  짧은 트랜잭션에서 현재 활성 상태를 다시 확인하고 AI 결과 또는 재계산한 규칙 결과를
  추천 세트에 저장한다. 이 경계가 ADR-0015의 기존 단일 저장 트랜잭션 설명을 대체한다.
- Groq 키는 로컬 `.env` 또는 운영 SSM SecureString에서만 주입하고 키, 인증 헤더, 전체
  프롬프트와 사용자 인증 정보를 로그에 남기지 않는다.

## 검토한 대안

- 규칙 결과만 사용하면 외부 장애와 비용은 줄지만 공모전의 AI 활용 결정을 충족하지 못한다.
- Groq를 신규 장소 적재와 추천 세트 저장 사이의 같은 DB 트랜잭션에서 호출하면 구현은
  단순하지만 최대 20초 동안 사용자 잠금과 DB 자원을 점유한다.
- AI 일부 결과를 서버에서 보정하면 응답 성공률은 높아지지만 어떤 규칙으로 만든 결과인지
  불명확해지므로 전체 폐기와 안정적 폴백을 선택했다.

## 영향 대상과 검사 근거

- 정본: `docs/product/features/place-selection.md`, `docs/product/mvp.md`,
  `docs/architecture/backend.md`, ADR-0015와 운영 배포 문서
- 코드·설정: `RecommendationService`, `GroqRecommendationClient`, `GroqConfig`,
  `application.yml`, Terraform SSM·ECS 비밀값 주입
- 테스트: Groq HTTP 계약과 추천 Service의 후보 상한, 정상 순서, 계약 위반, 외부 실패,
  저장 직전 비활성화 및 0건 경계
- 근거: Groq [공식 모델 문서](https://console.groq.com/docs/model/openai/gpt-oss-20b)와
  [Structured Outputs 문서](https://console.groq.com/docs/structured-outputs)에서
  2026-08-21 기준 `openai/gpt-oss-20b`와 `strict: true` 지원을 확인했다. 무료 기준은
  [공식 rate limit 문서](https://console.groq.com/docs/rate-limits)의 30 RPM·1,000 RPD·
  8,000 TPM·200,000 TPD이며 계정의 실제 제한이 우선한다.
- 미해결 항목: 없음. 무료 사용량은 운영 SLA가 아니며 제공자 정책 변경 시 모델 가용성을
  다시 확인한다.

## 변경 영향 검사 결과

- 검사 유형: 제품, 아키텍처, 보안
- 최종 판정: 통과
- 변경한 대상: 장소 선택·MVP·사용자 흐름·백엔드 아키텍처·ADR-0015·운영 문서,
  추천 Service와 Groq HTTP 클라이언트·설정·테스트, 로컬 환경 예시와 Terraform
  SSM·IAM·ECS 비밀값 주입
- 확인했지만 변경하지 않은 대상: 추천 Controller·DTO·Converter의 HTTP 경로, 요청·응답,
  상태 코드와 인증 계약, OpenAPI·Postman 예시, `CONTEXT.md`의 도메인 용어
- 확인하지 못한 대상: 로컬에 Terraform CLI가 없어 `terraform validate`를 실행하지
  못했고 운영 SSM·ECS 주입, 실제 Groq 호출과 프런트엔드 E2E는 실행하지 않았다.
- 미해결: 정본·코드·테스트 사이 불일치 없음
- 검사 근거: 전체 103개 테스트, Spotless와 전체 빌드, 하네스 semantic 검사 및
  `git diff --check` 통과. Groq 키·모델·이전 트랜잭션 표현을 저장소 검색으로 대조했다.
