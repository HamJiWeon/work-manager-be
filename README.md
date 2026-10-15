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


### 소셜 인증 결과 상태

Google·GitHub 인증 성공 후 `security.oauth.login-success-url`에 `status` 쿼리 파라미터를 추가한다.

- `SIGNUP_REQUIRED`: 기존 회원이 없는 소셜 계정이다. 회원·소셜 계정을 저장하지 않고 로그인 토큰도 발급하지 않는다. 기존 refresh 쿠키를 삭제한다.
- `LOGIN_COMPLETE`: 기존 회원이다. refresh 쿠키를 발급하며 `/auth/refresh`로 access token을 받을 수 있다.

`status`는 화면 분기용이며 인증 근거로 사용할 수 없다. 가입 동의·가입 완료 API와 프론트엔드 연결은 후속 구현 대상이다. 신규 계정의 소셜 인증 정보는 이번 단계에서 보관하지 않는다.
