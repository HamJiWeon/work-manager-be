package work.managerbe.card.repository;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;

/**
 * 기존 카드가 있는 H2 DB에 실제 순서 마이그레이션을 적용해 상태별 초기 순서와 제약을 확인한다.
 */
class CardOrderMigrationTest {
    private static final String MIGRATIONS = "classpath:db/migration";
    private static final String H2_MIGRATIONS = "classpath:db/vendor/h2";
    private static final String PREVIOUS_VERSION = "14";
    private static final String TARGET_VERSION = "16";
    @Test
    void 기존_카드는_보드와_상태별_ID순으로_번호를_부여받는다() throws SQLException {
        // given
        String url = "jdbc:h2:mem:card-order-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            var source = new SingleConnectionDataSource(connection, true);
            var jdbc = new JdbcTemplate(source);
            Flyway.configure().dataSource(source).locations(MIGRATIONS, H2_MIGRATIONS)
                    .target(PREVIOUS_VERSION).load().migrate();
            jdbc.update("INSERT INTO users (id, name, created_at, updated_at) VALUES (?, '사용자', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", userId);
            jdbc.update("INSERT INTO projects (id, user_id, code, name, created_at, updated_at) VALUES (1, ?, 'TEST', '프로젝트', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", userId);
            jdbc.update("INSERT INTO members (id, user_id, project_id, role, joined_at, created_at, updated_at) VALUES (1, ?, 1, 'OWNER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", userId);
            jdbc.update("INSERT INTO boards (id, project_id, name, sort_order, created_at, updated_at) VALUES (1, 1, '보드', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), (2, 1, '보드2', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            jdbc.update("""
                    INSERT INTO cards (id, user_id, member_id, project_id, board_id, title, content, status, created_at, updated_at)
                    VALUES (30, ?, 1, 1, 1, '마지막', '', 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           (10, ?, 1, 1, 1, '처음', '', 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           (20, ?, 1, 1, 1, '완료', '', 'DONE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           (40, ?, 1, 1, 2, '다른 보드', '', 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, userId, userId, userId, userId);
            // when
            Flyway.configure().dataSource(source).locations(MIGRATIONS, H2_MIGRATIONS)
                    .target(TARGET_VERSION).load().migrate();
            // then
            assertThat(jdbc.queryForList("SELECT sort_order FROM cards ORDER BY id", Integer.class)).containsExactly(0, 0, 1, 0);
            assertThatThrownBy(() -> jdbc.update("UPDATE cards SET sort_order = -1 WHERE id = 10"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("DELETE FROM projects WHERE id = 1");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cards", Integer.class)).isZero();
        }
    }
}
