package work.managerbe.project.repository;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 H2 마이그레이션을 적용하고 SQL 삭제로 V14의 프로젝트 연쇄 삭제 복구를 검증한다.
 */
class ProjectDeletionH2MigrationTest {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String H2_MIGRATION_LOCATION = "classpath:db/vendor/h2";
    private static final String TARGET_VERSION = "14";
    private static final UUID CREATOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long PROJECT_ID = 1L;
    private static final long MEMBER_ID = 1L;
    private static final long BOARD_ID = 1L;

    /**
     * V12의 기존 데이터를 V14까지 이전하는 경우와 신규 V14 DB 모두에서 프로젝트 삭제를 검증한다.
     * ORM을 거치지 않고 프로젝트만 삭제해 DB가 연관 데이터를 제거하고 사용자를 보존하는지 확인한다.
     */
    @ParameterizedTest
    @CsvSource({"12, 2", "14, 0"})
    void V14_적용_후_프로젝트를_삭제하면_연관_데이터만_연쇄_삭제된다(
            String initialVersion, int expectedMigrations) throws SQLException {
        // given
        String url = "jdbc:h2:mem:project-deletion-migration-" + UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            var dataSource = new SingleConnectionDataSource(connection, true);
            var jdbc = new JdbcTemplate(dataSource);
            migrate(dataSource, initialVersion);
            insertProjectData(jdbc);
            var result = migrate(dataSource, TARGET_VERSION);
            assertThat(result.migrationsExecuted).isEqualTo(expectedMigrations);
            assertThat(jdbc.queryForObject("SELECT status FROM cards WHERE project_id = ?",
                    String.class, PROJECT_ID)).isEqualTo("NOT_STARTED");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM "flyway_schema_history"
                    WHERE "version" = ? AND "success" = TRUE
                    """, Integer.class, TARGET_VERSION)).isOne();

            // when
            int deletedProjects = jdbc.update("DELETE FROM projects WHERE id = ?", PROJECT_ID);

            // then
            assertThat(deletedProjects).isOne();
            assertThat(count(jdbc, "projects", "id", PROJECT_ID)).isZero();
            assertThat(count(jdbc, "cards", "project_id", PROJECT_ID)).isZero();
            assertThat(count(jdbc, "boards", "project_id", PROJECT_ID)).isZero();
            assertThat(count(jdbc, "workspaces", "project_id", PROJECT_ID)).isZero();
            assertThat(count(jdbc, "members", "project_id", PROJECT_ID)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, CREATOR_ID)).isOne();
        }
    }

    /**
     * 애플리케이션과 동일한 공통 및 H2 전용 경로의 실제 SQL을 지정 버전까지 적용한다.
     */
    private static MigrateResult migrate(
            SingleConnectionDataSource dataSource, String version) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION, H2_MIGRATION_LOCATION)
                .target(version)
                .load()
                .migrate();
    }

    /**
     * 최종 스키마의 필수 컬럼과 복합 FK를 만족하는 프로젝트 연관 데이터를 준비한다.
     */
    private static void insertProjectData(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO users (id, name, created_at, updated_at)
                VALUES (?, '생성자', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, CREATOR_ID);
        jdbc.update("""
                INSERT INTO projects (id, user_id, code, name, created_at, updated_at)
                VALUES (?, ?, 'DELETE', '삭제할 프로젝트', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PROJECT_ID, CREATOR_ID);
        jdbc.update("""
                INSERT INTO members
                    (id, user_id, project_id, role, joined_at, created_at, updated_at)
                VALUES (?, ?, ?, 'OWNER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, MEMBER_ID, CREATOR_ID, PROJECT_ID);
        jdbc.update("""
                INSERT INTO workspaces (project_id, title, content, created_at, updated_at)
                VALUES (?, '워크스페이스', '내용', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PROJECT_ID);
        jdbc.update("""
                INSERT INTO boards (id, project_id, name, sort_order, created_at, updated_at)
                VALUES (?, ?, '진행 중', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, BOARD_ID, PROJECT_ID);
        jdbc.update("""
                INSERT INTO cards
                    (user_id, member_id, project_id, board_id, title, content, created_at, updated_at)
                VALUES (?, ?, ?, ?, '카드', '내용', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, CREATOR_ID, MEMBER_ID, PROJECT_ID, BOARD_ID);
    }

    private static int count(JdbcTemplate jdbc, String table, String column, long value) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                value
        );
    }
}
