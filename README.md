# work-manager-be

## OAuth 로컬 실행 설정

`oauth` 프로필과 기본 애플리케이션 실행에는 다음 환경 변수가 필요하다.

| 환경 변수 | 설명 |
| --- | --- |
| `JWT_SECRET` | 최소 32바이트 이상의 JWT 서명 비밀 |
| `GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 클라이언트 보안 비밀 |
| `GITHUB_CLIENT_ID` | GitHub OAuth App 클라이언트 ID |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App 클라이언트 보안 비밀 |
| `LOGIN_SUCCESS_URL` | 로그인 성공 후 이동할 프런트엔드 URL |
로컬 값은 `.env.example`을 복사한 `.env`에 입력하고, IntelliJ 실행 구성이나 셸에서 환경 변수로 주입한다. `.env`는 Git에서 제외되므로 실제 자격 증명을 커밋하지 않는다.

## 카드 수정 API

상태·순서 이동 및 부분 수정 계약은 [카드 수정 API](docs/card-update-api.md)를 참고한다.
