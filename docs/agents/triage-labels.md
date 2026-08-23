# Triage Status

`triage`는 GitHub label 대신 Local Markdown implementation ticket의 `Status:` 값을 쓴다.

| Status | 의미 |
|---|---|
| `needs-triage` | maintainer 판단이 필요하다. |
| `needs-info` | 요청자에게 추가 정보가 필요하다. |
| `ready-for-agent` | 완전히 명세되어 agent가 구현할 수 있다. |
| `ready-for-human` | 사람의 구현이나 직접 작업이 필요하다. |
| `wontfix` | 이 initiative에서 진행하지 않는다. |

완료 상태인 `resolved`는 `triage` 판정값이 아니다. 구현과 검증이 성공한 뒤 `implement`가
설정한다.
