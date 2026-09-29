# work-manager-be

## OAuth 로컬 실행 설정

`oauth` 프로필은 Google과 GitHub 로그인을 함께 등록하므로 다음 환경 변수가 모두 필요하다.

| 환경 변수 | 설명 |
| --- | --- |
| `GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 클라이언트 보안 비밀 |
| `GITHUB_CLIENT_ID` | GitHub OAuth App 클라이언트 ID |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App 클라이언트 보안 비밀 |
| `LOGIN_SUCCESS_URL` | 로그인 성공 후 이동할 프런트엔드 URL |

로컬 값은 `.env.example`을 복사한 `.env`에 입력하고, IntelliJ 실행 구성이나 셸에서 환경 변수로 주입한다. `.env`는 Git에서 제외되므로 실제 자격 증명을 커밋하지 않는다.
