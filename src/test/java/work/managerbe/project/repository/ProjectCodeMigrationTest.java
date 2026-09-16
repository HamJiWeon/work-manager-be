package work.managerbe.project.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V3의 실제 데이터를 V4로 이관하고 생성자별 유일성과 필수 생성자 관계를 검증한다.
 */
class ProjectCodeMigrationTest {
    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String PREVIOUS_VERSION = "3";
    private static final String TARGET_VERSION = "4";
    private static final UUID CREATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MISSING_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final long PROJECT_ID = 1L;

    @Test
    void OWNER를_생성자로_이관하고_기존_코드와_참여자를_유지한다() throws SQLException {
        // given
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            prepare(connection, jdbc);
            owner(jdbc, CREATOR_ID);
            jdbc.update("""
                    INSERT INTO members (user_id, project_id, role, joined_at, created_at, updated_at)
                    VALUES (?, ?, 'MEMBER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, OTHER_ID, PROJECT_ID);

            // when
            migrate(connection, TARGET_VERSION);

            // then
            assertThat(jdbc.queryForObject("SELECT user_id FROM projects WHERE id = ?", UUID.class, PROJECT_ID))
                    .isEqualTo(CREATOR_ID);
            assertThat(jdbc.queryForObject("SELECT code FROM projects WHERE id = ?", String.class, PROJECT_ID))
                    .isEqualTo("WORK_legacy");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void 같은_생성자의_같은_코드는_거절하고_다른_코드와_다른_생성자는_허용한다() throws SQLException {
        // given
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            prepare(connection, jdbc);
            owner(jdbc, CREATOR_ID);
            migrate(connection, TARGET_VERSION);

            // when / then
            assertThatThrownBy(() -> insert(connection, CREATOR_ID, "WORK_legacy"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));
            insert(connection, CREATOR_ID, "STUDY");
            insert(connection, OTHER_ID, "WORK_legacy");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM projects", Integer.class)).isEqualTo(3);
        }
    }

    @Test
    void 생성자가_없거나_존재하지_않는_사용자이면_저장을_거절한다() throws SQLException {
        // given
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            prepare(connection, jdbc);
            owner(jdbc, CREATOR_ID);
            migrate(connection, TARGET_VERSION);

            // when / then
            assertThatThrownBy(() -> insert(connection, null, "NULL"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23502"));
            assertThatThrownBy(() -> insert(connection, MISSING_ID, "MISSING"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23506"));
        }
    }

    @Test
    void OWNER가_없는_기존_프로젝트는_이관을_거절한다() throws SQLException {
        // given
        try (Connection connection = database()) {
            prepare(connection, jdbc(connection));

            // when / then
            assertThatThrownBy(() -> migrate(connection, TARGET_VERSION)).isInstanceOf(FlywayException.class);
        }
    }

    @Test
    void OWNER가_여러_명인_기존_프로젝트는_이관을_거절한다() throws SQLException {
        // given
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            prepare(connection, jdbc);
            owner(jdbc, CREATOR_ID);
            owner(jdbc, OTHER_ID);

            // when / then
            assertThatThrownBy(() -> migrate(connection, TARGET_VERSION)).isInstanceOf(FlywayException.class);
        }
    }

    @Test
    void 기존_생성자와_코드가_중복되면_데이터를_임의로_바꾸지_않고_이관을_거절한다() throws SQLException {
        // given
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            prepare(connection, jdbc);
            owner(jdbc, CREATOR_ID);
            jdbc.update("""
                    INSERT INTO projects (id, code, name, created_at, updated_at)
                    VALUES (2, 'WORK_legacy', '중복', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            jdbc.update("""
                    INSERT INTO members (user_id, project_id, role, joined_at, created_at, updated_at)
                    VALUES (?, 2, 'OWNER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, CREATOR_ID);

            // when / then
            assertThatThrownBy(() -> migrate(connection, TARGET_VERSION)).isInstanceOf(FlywayException.class);
        }
    }

    /**
     * 각 테스트에 독립적인 H2 DB 연결을 제공한다.
     */
    private static Connection database() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:project-code-" + UUID.randomUUID(), "sa", "");
    }

    private static JdbcTemplate jdbc(Connection connection) {
        return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    /**
     * 실제 Flyway 파일을 지정 버전까지 적용한다.
     */
    private static void migrate(Connection connection, String version) {
        Flyway.configure().dataSource(new SingleConnectionDataSource(connection, true))
                .locations(MIGRATION_LOCATION).target(version).load().migrate();
    }

    /**
     * 생성자 컬럼이 없는 이전 버전의 사용자와 프로젝트를 준비한다.
     */
    private static void prepare(Connection connection, JdbcTemplate jdbc) {
        migrate(connection, PREVIOUS_VERSION);
        jdbc.update("""
                INSERT INTO users (id, name, created_at, updated_at)
                VALUES (?, '생성자', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                       (?, '다른 사용자', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, CREATOR_ID, OTHER_ID);
        jdbc.update("""
                INSERT INTO projects (id, code, name, created_at, updated_at)
                VALUES (?, 'WORK_legacy', '기존 프로젝트', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PROJECT_ID);
        jdbc.execute("ALTER TABLE projects ALTER COLUMN id RESTART WITH 10");
    }

    private static void owner(JdbcTemplate jdbc, UUID userId) {
        jdbc.update("""
                INSERT INTO members (user_id, project_id, role, joined_at, created_at, updated_at)
                VALUES (?, ?, 'OWNER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, PROJECT_ID);
    }

    /**
     * 서비스 검증을 거치지 않는 INSERT로 DB 제약 자체를 검증한다.
     */
    private static void insert(Connection connection, UUID creatorId, String code) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO projects (user_id, code, name, created_at, updated_at)
                VALUES (?, ?, '프로젝트', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            statement.setObject(1, creatorId);
            statement.setString(2, code);
            statement.executeUpdate();
        }
    }
}
