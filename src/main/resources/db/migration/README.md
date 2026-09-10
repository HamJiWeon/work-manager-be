# DB 마이그레이션

테이블, 인덱스, PK/FK, UNIQUE, CHECK 등 스키마 변경은 이 디렉터리의 Flyway SQL로 관리한다.
현재 도메인 테이블은 구현 전이므로 실행할 마이그레이션은 아직 없다.

- 첫 스키마 파일: `V1__create_initial_tables.sql`
- 이후 변경 예시: `V2__add_card_constraints.sql`
- 버전은 중복 없이 증가시키고 버전과 설명 사이에는 밑줄 두 개를 사용한다.
- 적용된 파일을 수정하거나 삭제하지 않고 새 버전 파일에 변경 SQL을 작성한다.
- 제약조건에는 `uk_cards_project_number`처럼 목적을 알 수 있는 이름을 지정한다.
- Hibernate는 `ddl-auto=validate`로 엔티티와 스키마를 검증한다. 제약조건 전체를 검증하는 것은 아니므로 제약 동작은 별도 DB 테스트로 확인한다.

Flyway는 애플리케이션 시작 시 SQL을 순서대로 실행하고 `flyway_schema_history`에 적용 이력을 기록한다.
SQL 파일을 Git으로 관리하여 변경 내용도 보존한다. H2 인메모리 DB는 앱 종료 시 이력이 사라지며 다음 실행에서 처음부터 재적용한다.
H2와 PostgreSQL은 SQL 및 제약 동작에 차이가 있으므로 PostgreSQL을 사용하는 환경에서도 마이그레이션을 검증한다.
