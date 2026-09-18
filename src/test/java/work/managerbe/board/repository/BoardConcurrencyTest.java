package work.managerbe.board.repository;

import jakarta.persistence.EntityManager;
import work.managerbe.member.domain.Member;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import work.managerbe.board.domain.Board;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.service.BoardService;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프로젝트 코드로 요청한 독립 트랜잭션의 생성을 겹쳐 프로젝트 잠금 대기와 커밋 후 보드 순서를 검증한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:board-concurrency-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class BoardConcurrencyTest {

    private static final int WORKER_COUNT = 2;
    private static final int COMPLETION_TIMEOUT_SECONDS = 10;
    private static final int LOCK_WAIT_MILLIS = 500;

    private final EntityManager entityManager;
    private final BoardService boardService;
    private final TransactionTemplate transaction;

    @Autowired
    BoardConcurrencyTest(EntityManager entityManager, BoardService boardService,
                         PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.boardService = boardService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 첫 생성의 커밋을 지연해 두 번째 요청이 기다리는지 확인하고 새 트랜잭션에서 저장값을 검증한다.
     */
    @Test
    void 동시_생성은_프로젝트_잠금을_기다리고_중복_없는_순서로_저장된다() throws Exception {
        // given
        User user = User.create("작성자", "concurrency@example.com", null);
        Project project = Project.create(user, "CONCURRENT", "동시 생성", null);
        transaction.executeWithoutResult(status -> {
            entityManager.persist(user);
            entityManager.persist(project);
            entityManager.persist(Member.create(user, project, "MEMBER"));
        });
        var firstCreated = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var allowFirstCommit = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(WORKER_COUNT)) {
            try {
                // when
                var first = executor.submit(() -> transaction.execute(status -> {
                    var response = boardService.create(user.getId(), project.getCode(), new BoardCreateRequest("첫 보드"));
                    entityManager.flush();
                    firstCreated.countDown();
                    await(allowFirstCommit);
                    return response;
                }));
                await(firstCreated);
                var second = executor.submit(() -> transaction.execute(status -> {
                    secondStarted.countDown();
                    return boardService.create(user.getId(), project.getCode(), new BoardCreateRequest("둘째 보드"));
                }));
                await(secondStarted);

                // then
                assertThatThrownBy(() -> second.get(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                allowFirstCommit.countDown();
                var firstResponse = first.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                var secondResponse = second.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertThat(firstResponse.sortOrder()).isZero();
                assertThat(secondResponse.sortOrder()).isEqualTo(1);
                transaction.executeWithoutResult(status -> {
                    var boards = entityManager.createQuery(
                                    "select b from Board b where b.project.id = :projectId order by b.sortOrder", Board.class)
                            .setParameter("projectId", project.getId()).getResultList();
                    assertThat(boards).extracting(Board::getId)
                            .containsExactly(firstResponse.id(), secondResponse.id());
                    assertThat(boards).extracting(Board::getSortOrder).containsExactly(0, 1);
                });
            } finally {
                allowFirstCommit.countDown();
                executor.shutdownNow();
            }
        } finally {
            transaction.executeWithoutResult(status -> {
                entityManager.createQuery("delete from Board b where b.project.id = :projectId")
                        .setParameter("projectId", project.getId()).executeUpdate();
                entityManager.createQuery("delete from Member m where m.project.id = :projectId")
                        .setParameter("projectId", project.getId()).executeUpdate();
                entityManager.remove(entityManager.find(Project.class, project.getId()));
                entityManager.remove(entityManager.find(User.class, user.getId()));
            });
        }
    }

    /**
     * 작업 스레드의 실패가 무한 대기로 이어지지 않도록 제한 시간 내 동기화 신호를 기다린다.
     */
    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시 생성 테스트 대기가 중단되었습니다.", exception);
        }
    }
}
