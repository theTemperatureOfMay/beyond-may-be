# Postman

`Beyond May Be API/`의 요청 YAML과 `.resources/` 예시 YAML은 팀의 API 요청·문서·예시 응답 원본이다. 유료 Postman Team Workspace와 공개 문서는 사용하지 않는다.

## Local Folder 사용

Postman Desktop에서 이 `postman/` 폴더를 Local Folder로 연결해 Local View를 사용한다.

- Git pull 또는 브랜치 전환으로 요청·예시 YAML이 바뀌면 Local View에서 최신 파일을 사용한다.
- `.postman/`과 `postman/` 하위 폴더는 Postman이 연결 상태와 Cloud 리소스를 관리하는 파일이다. 직접 수정하지 않는다.
- API 문서·요청·saved example은 `Beyond May Be API/`에서 수정한다. 삭제된 이전 Collection JSON은 복원하지 않는다.
- Local Folder를 사용하지 않는 환경에서는 Postman에서 컬렉션을 내보내 개인 Workspace로 가져온다.

## 공용 Collection 규칙

- 공유 변수는 `baseUrl` 하나이며 기본값은 `http://localhost:8080`이다.
- 현재 확인 가능한 요청에는 `System / Health`와 인증이 필요한
  `장소 선택 / GET {{baseUrl}}/api/v1/places/{placeId}`,
  `장소 선택 / POST {{baseUrl}}/api/v1/recommendations/sets`,
  `장소 선택 / GET {{baseUrl}}/api/v1/recommendations`,
  `장소 선택 / POST {{baseUrl}}/api/v1/recommendations/{batchNumber}/reactions`,
  `코스 / POST {{baseUrl}}/api/v1/courses/ai-generation`,
  `코스 / GET {{baseUrl}}/api/v1/courses/{courseId}/exploration`,
  `방문 기록 / GET {{baseUrl}}/api/v1/explorations?status={status}`,
  `팀 탐험 지도 / GET {{baseUrl}}/api/v1/explorations/{explorationId}`,
  `방문 기록 / POST {{baseUrl}}/api/v1/explorations/{explorationId}/complete`,
  `팀 탐험 지도 / POST {{baseUrl}}/api/v1/explorations/{explorationId}/leave`,
  `팀 탐험 지도 / GET {{baseUrl}}/api/v1/explorations/{explorationId}/nearby-places`,
  `팀 탐험 지도 / PATCH {{baseUrl}}/api/v1/explorations/{explorationId}/participants/me/location-sharing`,
  `팀 탐험 지도 / POST {{baseUrl}}/api/v1/visits`,
  `방문 기록 / GET {{baseUrl}}/api/v1/visits?explorationId={explorationId}`,
  `방문 기록 / GET {{baseUrl}}/api/v1/visits/visited-places?explorationId={explorationId}`,
  `방문 기록 / POST {{baseUrl}}/api/v1/visits/{visitId}/photos`가 있다.
- 새 API 요청은 해당 도메인 폴더에 추가한다.
- 토큰, 비밀번호, 실제 환경별 비밀값, 실사용자 정보는 공용 Collection에 넣지 않는다.
- Postman UI에서 설명·예시를 수정했다면 변경 내용이 원본 요청·예시 YAML에 반영됐는지 확인한다.

## 새 API 문서·예시 체크리스트

- [ ] 도메인 폴더와 요청 이름
- [ ] 요청 목적, 인증 필요 여부, header/query/path/body 설명
- [ ] 성공 saved example
- [ ] 실제로 적용되는 대표 오류 saved example
- [ ] 비밀값·실사용자 정보 없음

## 개인 환경

- 개인 변수는 `local.postman_environment.json`으로 저장한다.
- `*.postman_environment.json`은 Git에서 제외된다.
- 인증 기능이 추가된 뒤 `accessToken` 같은 개인 변수는 이 파일에서만 관리한다.
