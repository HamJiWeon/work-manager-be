package work.managerbe.oauthaccount.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL 16에서 V5의 레거시 OAuth provider 데이터를 V6 enum 값으로 이관하고 제약을 검증한다.
 */
@Testcontainers
class OAuthProviderPostgresMigrationTest {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String PREVIOUS_VERSION = "5";
    private static final String TARGET_VERSION = "6";
    private static final String CHECK_VIOLATION = "23514";
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @Test
    void 소문자_provider를_enum_값으로_변환하고_허용되지_않은_값을_거절한다() throws SQLException {
        // given
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            migrate(connection, PREVIOUS_VERSION);
            insertUser(jdbc);
            insertOAuthAccount(jdbc, "google", "google-user");
            insertOAuthAccount(jdbc, "github", "github-user");

            // when
            migrate(connection, TARGET_VERSION);

            // then
            assertThat(jdbc.queryForList(
                    "SELECT provider FROM oauth_accounts ORDER BY provider_user_id",
                    String.class
            )).containsExactly("GITHUB", "GOOGLE");
            assertThatThrownBy(() -> insertOAuthAccount(jdbc, "NAVER", "naver-user"))
                    .isInstanceOfSatisfying(Exception.class, error ->
                            assertThat(sqlState(error)).isEqualTo(CHECK_VIOLATION));
        }
    }

    @Test
    void 지원하지_않는_provider가_있으면_변환과_제약_추가를_모두_롤백한다() throws SQLException {
        // given
        try (Connection connection = database()) {
            JdbcTemplate jdbc = jdbc(connection);
            migrate(connection, PREVIOUS_VERSION);
            insertUser(jdbc);
            insertOAuthAccount(jdbc, "naver", "legacy-user");

            // when / then
            assertThatThrownBy(() -> migrate(connection, TARGET_VERSION))
                    .isInstanceOf(FlywayException.class)
                    .rootCause()
                    .isInstanceOfSatisfying(SQLException.class,
                            error -> assertThat(error.getSQLState()).isEqualTo(CHECK_VIOLATION));
            assertThat(jdbc.queryForObject(
                    "SELECT provider FROM oauth_accounts WHERE provider_user_id = 'legacy-user'",
                    String.class
            )).isEqualTo("naver");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM flyway_schema_history
                    WHERE version = ? AND success = TRUE
                    """, Integer.class, TARGET_VERSION)).isZero();
        }
    }

    private static Connection database() throws SQLException {
        Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try {
            String schema = "oauth_provider_" + UUID.randomUUID().toString().replace("-", "");
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

    private static void migrate(Connection connection, String version) throws SQLException {
        Flyway.configure()
                .dataSource(new SingleConnectionDataSource(connection, true))
                .locations(MIGRATION_LOCATION)
                .defaultSchema(connection.getSchema())
                .schemas(connection.getSchema())
                .target(version)
                .load()
                .migrate();
    }

    private static void insertUser(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO users (id, name, created_at, updated_at)
                VALUES (?, 'OAuth 사용자', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, USER_ID);
    }

    private static void insertOAuthAccount(JdbcTemplate jdbc, String provider, String providerUserId) {
        jdbc.update("""
                INSERT INTO oauth_accounts (user_id, provider, provider_user_id, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, USER_ID, provider, providerUserId);
    }

    private static String sqlState(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }
}
