package work.managerbe.project.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL 16에 V1부터 V13까지 적용하고 프로젝트 FK의 cascade 삭제 동작을 검증한다.
 */
@Testcontainers
class ProjectDeletionPostgresMigrationTest {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String TARGET_VERSION = "13";
    private static final UUID CREATOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long PROJECT_ID = 1L;
    private static final long MEMBER_ID = 1L;
    private static final long BOARD_ID = 1L;

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    /**
     * 프로젝트 한 행만 삭제해 카드, 보드, 워크스페이스, 멤버는 제거되고 사용자는 유지되는지 확인한다.
     */
    @Test
    void 프로젝트를_삭제하면_연관_데이터만_cascade로_삭제된다() throws SQLException {
        // given
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            migrate(connection);
            insertProjectData(jdbc);

            assertThat(jdbc.queryForObject("SELECT status FROM cards WHERE project_id = ?",
                    String.class, PROJECT_ID)).isEqualTo("NOT_STARTED");
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    jdbc.update("UPDATE cards SET status = 'UNKNOWN' WHERE project_id = ?", PROJECT_ID))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

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
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM flyway_schema_history
                    WHERE version = ? AND success = TRUE
                    """, Integer.class, TARGET_VERSION)).isOne();
        }
    }

    /**
     * 테스트마다 전용 PostgreSQL 스키마를 만들어 다른 마이그레이션 테스트와 데이터를 격리한다.
     */
    private static Connection database() throws SQLException {
        Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try {
            String schema = "project_deletion_" + UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            }
            connection.setSchema(schema);
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    private static JdbcTemplate jdbc(Connection connection) {
        return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    /**
     * 운영 환경과 동일한 마이그레이션 파일을 V13까지 순서대로 적용한다.
     */
    private static void migrate(Connection connection) {
        Flyway.configure()
                .dataSource(new SingleConnectionDataSource(connection, true))
                .locations(MIGRATION_LOCATION)
                .defaultSchema(connectionSchema(connection))
                .schemas(connectionSchema(connection))
                .target(TARGET_VERSION)
                .load()
                .migrate();
    }

    private static String connectionSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException exception) {
            throw new IllegalStateException("PostgreSQL 테스트 스키마를 조회할 수 없습니다.", exception);
        }
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
