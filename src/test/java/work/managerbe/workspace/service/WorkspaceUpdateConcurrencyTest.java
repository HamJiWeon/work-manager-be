package work.managerbe.workspace.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import work.managerbe.project.domain.Project;
import work.managerbe.workspace.domain.Workspace;
import work.managerbe.workspace.dto.request.WorkspaceUpdateRequest;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 독립 트랜잭션의 워크스페이스 부분 수정을 겹쳐 행 잠금과 변경값 보존을 검증한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-update-concurrency-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class WorkspaceUpdateConcurrencyTest {

    private static final int WORKER_COUNT = 2;
    private static final int COMPLETION_TIMEOUT_SECONDS = 10;
    private static final int LOCK_WAIT_MILLIS = 500;

    private final EntityManager entityManager;
    private final WorkspaceService workspaceService;
    private final TransactionTemplate transaction;

    @Autowired
    WorkspaceUpdateConcurrencyTest(EntityManager entityManager, WorkspaceService workspaceService,
                                 PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.workspaceService = workspaceService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 제목 수정이 커밋될 때까지 내용 수정이 기다린 뒤 최신 값을 읽어 두 변경을 모두 저장한다.
     */
    @Test
    void 동시_부분_수정은_워크스페이스_잠금을_기다리고_두_변경을_모두_저장한다() throws Exception {
        // given
        User creator = User.create("생성자", "workspace-update-concurrency@example.com", null);
        Project project = Project.create(creator, "CONCURRENT_UPDATE", "기존 이름", "기존 설명");
        Workspace workspace = Workspace.create(project, "기존 제목", "기존 내용");
        transaction.executeWithoutResult(status -> {
            entityManager.persist(creator);
            entityManager.persist(project);
            entityManager.persist(workspace);
        });
        UUID creatorId = creator.getId();
        Long projectId = project.getId();
        Long workspaceId = workspace.getId();
        var firstUpdated = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var allowFirstCommit = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(WORKER_COUNT)) {
            try {
                // when
                var first = executor.submit(() -> transaction.execute(status -> {
                    var response = workspaceService.update(
                            creatorId,
                            "CONCURRENT_UPDATE",
                            workspaceId,
                            creatorId,
                            new WorkspaceUpdateRequest("변경된 제목", null)
                    );
                    firstUpdated.countDown();
                    await(allowFirstCommit);
                    return response;
                }));
                await(firstUpdated);

                var second = executor.submit(() -> transaction.execute(status -> {
                    secondStarted.countDown();
                    return workspaceService.update(
                            creatorId,
                            "CONCURRENT_UPDATE",
                            workspaceId,
                            creatorId,
                            new WorkspaceUpdateRequest(null, "변경된 내용")
                    );
                }));
                await(secondStarted);

                // then
                assertThatThrownBy(() -> second.get(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                allowFirstCommit.countDown();
                first.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                second.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                transaction.executeWithoutResult(status -> {
                    Workspace updated = entityManager.find(Workspace.class, workspaceId);
                    assertThat(updated.getTitle()).isEqualTo("변경된 제목");
                    assertThat(updated.getContent()).isEqualTo("변경된 내용");
                });
            } finally {
                allowFirstCommit.countDown();
                executor.shutdownNow();
            }
        } finally {
            transaction.executeWithoutResult(status -> {
                entityManager.remove(entityManager.find(Workspace.class, workspaceId));
                entityManager.remove(entityManager.find(Project.class, projectId));
                entityManager.remove(entityManager.find(User.class, creatorId));
            });
        }
    }

    /**
     * 작업 스레드가 실패해도 테스트가 무한 대기하지 않도록 제한 시간 내 신호를 기다린다.
     */
    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("워크스페이스 동시 수정 테스트 대기가 중단되었습니다.", exception);
        }
    }
}
