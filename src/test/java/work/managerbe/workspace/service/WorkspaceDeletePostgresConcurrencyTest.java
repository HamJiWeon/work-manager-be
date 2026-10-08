package work.managerbe.workspace.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import work.managerbe.global.exception.workspace.WorkspaceErrorCode;
import work.managerbe.global.exception.workspace.WorkspaceException;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;
import work.managerbe.workspace.domain.Workspace;
import work.managerbe.workspace.dto.request.WorkspaceUpdateRequest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL 16에서 독립 트랜잭션의 수정 및 삭제 잠금과 최종 상태를 검증한다. */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
@Testcontainers
class WorkspaceDeletePostgresConcurrencyTest {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    private static final String PROJECT_CODE = "DELETE_CONCURRENT";
    private static final int WORKER_COUNT = 2;
    private static final int TIMEOUT_SECONDS = 10;
    private static final int LOCK_WAIT_MILLIS = 500;

    private final EntityManager entityManager;
    private final WorkspaceService workspaceService;
    private final TransactionTemplate transaction;
    private User creator;
    private Project project;
    private Workspace workspace;

    @Autowired
    WorkspaceDeletePostgresConcurrencyTest(EntityManager entityManager, WorkspaceService workspaceService,
                                                     PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.workspaceService = workspaceService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 스레드마다 새 영속성 컨텍스트에서 접근할 수 있도록 준비 데이터를 커밋한다. */
    @BeforeEach
    void 동시_삭제_데이터를_준비한다() {
        creator = User.create("생성자", "delete-concurrency@example.com", null);
        project = Project.create(creator, PROJECT_CODE, "프로젝트", null);
        workspace = Workspace.create(project, "기존 제목", "내용");
        transaction.executeWithoutResult(status -> {
            entityManager.persist(creator);
            entityManager.persist(project);
            entityManager.persist(workspace);
        });
    }

    /** 성공 및 실패 시에도 테스트 데이터를 자식부터 정리한다. */
    @AfterEach
    void 동시_삭제_데이터를_정리한다() {
        transaction.executeWithoutResult(status -> {
            Workspace remaining = entityManager.find(Workspace.class, workspace.getId());
            if (remaining != null) {
                entityManager.remove(remaining);
                entityManager.flush();
            }
            entityManager.remove(entityManager.find(Project.class, project.getId()));
            entityManager.flush();
            entityManager.remove(entityManager.find(User.class, creator.getId()));
        });
    }

    /** 수정 트랜잭션이 잠금을 가진 동안 삭제가 대기하고 수정 커밋 후 삭제되는지 확인한다. */
    @Test
    void 수정이_먼저_잠그면_삭제는_커밋까지_기다린_후_행을_제거한다() throws Exception {
        // given
        var firstLocked = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(WORKER_COUNT)) {
            try {
                // when
                var first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                    workspaceService.update(creator.getId(), PROJECT_CODE, workspace.getId(), creator.getId(),
                            new WorkspaceUpdateRequest("변경된 제목", null));
                    firstLocked.countDown();
                    await(allowCommit);
                }));
                await(firstLocked);
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    workspaceService.delete(creator.getId(), PROJECT_CODE, workspace.getId(), creator.getId());
                });
                await(secondStarted);

                // then
                assertThatThrownBy(() -> second.get(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                allowCommit.countDown();
                first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                삭제_후_상태를_검증한다();
            } finally {
                allowCommit.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    /** 삭제가 먼저 잠금을 획득하면 수정과 중복 삭제가 커밋 이후 대상 없음으로 실패한다. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 삭제가_먼저_잠그면_후속_수정과_삭제는_대기_후_대상_없음으로_실패한다(boolean update) throws Exception {
        // given
        var firstLocked = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(WORKER_COUNT)) {
            try {
                // when
                var first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                    workspaceService.delete(creator.getId(), PROJECT_CODE, workspace.getId(), creator.getId());
                    entityManager.flush();
                    firstLocked.countDown();
                    await(allowCommit);
                }));
                await(firstLocked);
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    if (update) {
                        workspaceService.update(creator.getId(), PROJECT_CODE, workspace.getId(), creator.getId(),
                                new WorkspaceUpdateRequest("변경된 제목", null));
                    } else {
                        workspaceService.delete(creator.getId(), PROJECT_CODE, workspace.getId(), creator.getId());
                    }
                });
                await(secondStarted);

                // then
                assertThatThrownBy(() -> second.get(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                allowCommit.countDown();
                first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertThatThrownBy(() -> second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .isInstanceOfSatisfying(ExecutionException.class, exception -> {
                            assertThat(exception.getCause()).isInstanceOf(WorkspaceException.class);
                            WorkspaceException cause = (WorkspaceException) exception.getCause();
                            assertThat(cause.getErrorCode()).isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
                        });
                삭제_후_상태를_검증한다();
            } finally {
                allowCommit.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    /** 작업 완료 이후 새 트랜잭션에서 물리 삭제와 부모 데이터 보존을 확인한다. */
    private void 삭제_후_상태를_검증한다() {
        transaction.executeWithoutResult(status -> {
            assertThat(entityManager.find(Workspace.class, workspace.getId())).isNull();
            assertThat(entityManager.find(Project.class, project.getId())).isNotNull();
            assertThat(entityManager.find(User.class, creator.getId())).isNotNull();
        });
    }

    /** 실패한 작업 때문에 테스트가 무한 대기하지 않도록 신호 대기 시간을 제한한다. */
    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시 삭제 테스트 대기가 중단되었습니다.", exception);
        }
    }
}
