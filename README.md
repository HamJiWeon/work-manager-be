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
```

OAuth를 사용하지 않는 실행과 테스트는 `oauth` 프로필이나 OAuth 환경변수를 요구하지 않는다.
