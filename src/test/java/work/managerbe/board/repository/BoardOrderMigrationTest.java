package work.managerbe.board.repository;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V2 데이터가 존재하는 H2에 실제 V3를 적용해 프로젝트별 순서 보정과 기본값을 검증한다.
 */
class BoardOrderMigrationTest {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String PREVIOUS_VERSION = "2";
    private static final String ORDER_VERSION = "3";

    /**
     * 비연속 순서와 동률, 1부터 시작하는 순서를 넣고 고정된 기대값으로 변환 결과를 검증한다.
     */
    @Test
    void 기존_보드의_상대_순서를_유지하면서_프로젝트별로_0부터_재정렬한다() throws SQLException {
        // given
        String url = "jdbc:h2:mem:board-order-migration-" + UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            var dataSource = new SingleConnectionDataSource(connection, true);
            var jdbc = new JdbcTemplate(dataSource);
            Flyway.configure().dataSource(url, "sa", "").locations(MIGRATION_LOCATION)
                    .target(PREVIOUS_VERSION).load().migrate();
            jdbc.update("""
                    INSERT INTO projects (id, code, name, created_at, updated_at)
                    VALUES (1, 'GAPS', '비연속', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           (2, 'ONE', '1부터 시작', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            jdbc.update("""
                    INSERT INTO boards (id, project_id, name, sort_order, created_at, updated_at)
                    VALUES (10, 1, '마지막', 90, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           (30, 1, '동률 뒤', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           (20, 1, '동률 앞', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           (40, 1, '처음', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           (50, 2, '둘째', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           (60, 2, '첫째', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);

            // when
            var result = Flyway.configure().dataSource(url, "sa", "").locations(MIGRATION_LOCATION)
                    .target(ORDER_VERSION).load().migrate();

            // then
            assertThat(result.migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT id FROM boards WHERE project_id = 1 ORDER BY sort_order", Long.class))
                    .containsExactly(40L, 20L, 30L, 10L);
            assertThat(jdbc.queryForList("SELECT sort_order FROM boards WHERE project_id = 1 ORDER BY sort_order", Integer.class))
                    .containsExactly(0, 1, 2, 3);
            assertThat(jdbc.queryForList("SELECT id FROM boards WHERE project_id = 2 ORDER BY sort_order", Long.class))
                    .containsExactly(60L, 50L);
            assertThat(jdbc.queryForList("SELECT sort_order FROM boards WHERE project_id = 2 ORDER BY sort_order", Integer.class))
                    .containsExactly(0, 1);
            jdbc.update("""
                    INSERT INTO boards (id, project_id, name, created_at, updated_at)
                    VALUES (70, 2, '기본값', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            assertThat(jdbc.queryForObject("SELECT sort_order FROM boards WHERE id = 70", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'BOARD_ORDER_MIGRATION'
                    """, Integer.class)).isZero();
        }
    }
}
