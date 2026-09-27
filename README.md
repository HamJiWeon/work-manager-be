# work-manager-be

## OAuth 실행

OAuth 로그인을 사용할 때는 `oauth` 프로필과 클라이언트 환경변수를 설정한다.

```text
SPRING_PROFILES_ACTIVE=dev,oauth
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
JWT_SECRET=32바이트_이상_비밀키
LOGIN_SUCCESS_URL=http://localhost:3000/oauth/callback
```

데스크톱과 모바일 웹은 동일한 반응형 웹 콜백 페이지를 사용한다.

```text
/oauth2/authorization/google
/oauth2/authorization/github
```

OAuth 인증이 성공하면 서버는 `LOGIN_SUCCESS_URL`로 이동한다. 콜백 페이지는
`GET /auth/csrf`에서 CSRF 토큰을 발급받은 후 `POST /auth/refresh`를 호출해
HttpOnly 쿠키의 Refresh Token을 Access Token으로 교환한다.
