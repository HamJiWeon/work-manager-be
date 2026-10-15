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

`status`는 화면 분기용이며 인증 근거로 사용할 수 없다. 가입 동의·가입 완료 API와 약관 화면 연동은 후속 구현 대상이다.


### 가입 전 임시 상태

신규 소셜 인증 결과는 `pending_registrations`에 저장하며 정식 `users`와 `oauth_accounts`는 생성하지 않는다.
`PENDING_REGISTRATION` 쿠키는 10분간 유효한 무작위 토큰이며 HttpOnly, SameSite=Lax, 기존 secure-cookie 설정을 적용한다.
Lax는 소셜 제공자에서 돌아오는 최상위 GET 콜백에 이전 쿠키를 전달해 재인증 시 이전 임시 상태를 폐기하기 위한 설정이다.
DB에는 원문 대신 SHA-256 해시를 저장하며 조회해도 만료 시간이 연장되지 않는다.
만료된 정보는 즉시 조회할 수 없고, 10분 간격으로 DB에서도 삭제한다. 기존 회원 로그인 시 해당 브라우저의 임시 상태를 폐기한다.

- `GET /auth/registration`: 유효한 쿠키로 `{name, provider, expiresAt}`를 반환한다. `expiresAt`은 UTC 기준이며 응답은 `Cache-Control: no-store`다. 쿠키 누락·만료·위조 시 401이다.
- `DELETE /auth/registration`: 임시 가입 정보와 쿠키를 삭제한다. `/auth/csrf`로 발급받은 CSRF 토큰 헤더가 필요하다.

임시 쿠키는 `/users/me` 등 보호 API나 `/auth/refresh`의 인증 수단으로 사용할 수 없다.
약관 목록과 동의 제출·정식 회원 전환은 아직 제공하지 않는다. 프론트의 기존 약관 화면은 `terms`도 요구하므로 이 조회 API만으로 가입 폼이 완성되지는 않는다.
