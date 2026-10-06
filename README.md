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


## Member API

[CRUD 명세](https://app.notion.com/p/CRUD-5ed16900ac5182ea8941814dc66c7163)를 기준으로 구현한다.
경로의 `userId`는 프로젝트 생성자 UUID이고, `projectId`는 프로젝트의 숫자 ID이다.
요청자 권한은 경로가 아니라 JWT principal의 활성 멤버 관계로 검증한다.

| 기능 | 메서드 | 경로 | 성공 상태 |
| --- | --- | --- | --- |
| 추가 | POST | `/{userId}/{projectId}/members` | 201 |
| 단건 조회 | GET | `/{userId}/{projectId}/{memberId}` | 200 |
| 목록 조회 | GET | `/{userId}/{projectId}/members?page=0&size=20` | 200 |
| 역할 변경 | PATCH | `/{userId}/{projectId}/{memberId}` | 200 |
| 탈퇴 처리 | DELETE | `/users/{userId}/projects/{projectId}/members/{memberId}` | 204 |

모든 기능은 `/users/{userId}/projects/{projectId}/members` 경로에서도 제공하며,
단건 조회·수정·삭제는 여기에 `/{memberId}`를 붙인다.
목록은 활성 멤버만 `joinedAt ASC, id ASC`로 정렬하고 `items`, `page`, `size`,
`totalElements`, `totalPages`를 반환한다. 조회 크기는 1~100이다.

조회는 활성 참여자에게 허용하고, 추가·역할 변경·삭제는 OWNER에게만 허용한다.
추가 요청은 `userId`가 필수이고 `role`을 생략하거나 null로 전달하면 MEMBER를 사용한다.
추가와 수정에서 허용하는 역할은 MEMBER와 ADMIN이며, 소유권 이전은 별도 정책이 필요하므로
OWNER 생성·승격·강등·삭제는 409 `MEMBER_OWNER_PROTECTED`로 거절한다.

삭제는 `leftAt`만 기록하여 기존 카드 담당자와 참여 이력을 보존한다.
탈퇴한 멤버의 일반 조회는 404 `MEMBER_NOT_FOUND`이며 신규 카드 생성의 담당자로 지정할 수 없다.
재가입 정책이 정해지기 전까지 활성·탈퇴 여부와 관계없이 기존 프로젝트·사용자 조합의
추가 요청은 409 `MEMBER_DUPLICATE`로 거절한다.
기존 컬럼과 유일성 제약을 사용하므로 스키마 마이그레이션은 추가하지 않는다.
