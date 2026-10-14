package work.managerbe.board.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.mockito.stubbing.Answer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import work.managerbe.board.domain.Board;
import work.managerbe.board.dto.response.BoardProgressResponse;
import work.managerbe.board.dto.response.BoardSliceResponse;
import work.managerbe.card.domain.Card;
import work.managerbe.card.domain.CardStatus;
import work.managerbe.card.repository.CardRepository;
import work.managerbe.member.domain.Member;
import work.managerbe.member.domain.MemberRole;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/**
 * PostgreSQL 16에서 실제 서비스의 목록 조회와 집계 사이에 카드 변경을 커밋하여
 * REPEATABLE_READ가 동일 요청의 완료 여부와 완료율을 같은 스냅샷으로 유지하는지 검증한다.
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
@Testcontainers
class BoardProgressPostgresConcurrencyTest {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String PROJECT_CODE = "PROGRESS";
    private static final int FIRST_PAGE = 0;
    private static final int PAGE_SIZE = 20;
    private static final int WORKER_COUNT = 1;
    private static final int CARD_POSITION = 0;
    private static final int TIMEOUT_SECONDS = 10;
    private static final double COMPLETE_RATE = 100.0;
    private static final double INCOMPLETE_RATE = 0.0;

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    @MockitoSpyBean
    private CardRepository cardRepository;

    private final EntityManager entityManager;
    private final BoardService boardService;
    private final TransactionTemplate transaction;
    private User owner;
    private Project project;
    private Board board;
    private Card card;

    @Autowired
    BoardProgressPostgresConcurrencyTest(EntityManager entityManager, BoardService boardService,
                                        PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.boardService = boardService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 목록 조회 후 집계 직전에만 대기하여 다른 트랜잭션의 상태 변경 커밋을 보장한다.
     * 현재 응답은 변경 이전 상태를, 후속 요청은 변경 이후 상태를 반환해야 한다.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 집계_전에_카드_상태가_변경되어도_목록과_완료율은_조회_시점을_유지한다(boolean completed)
            throws Exception {
        // given
        prepareData(completed ? CardStatus.DONE : CardStatus.IN_PROGRESS);
        CountDownLatch listingRead = new CountDownLatch(1);
        CountDownLatch changeCommitted = new CountDownLatch(1);
        AtomicBoolean firstAggregation = new AtomicBoolean(true);
        Answer<?> repositoryDelegate = mockingDetails(cardRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            if (firstAggregation.getAndSet(false)) {
                listingRead.countDown();
                await(changeCommitted);
            }
            return repositoryDelegate.answer(invocation);
        }).when(cardRepository).countProgressByBoardIds(List.of(board.getId()), CardStatus.DONE);

        try (var executor = Executors.newFixedThreadPool(WORKER_COUNT)) {
            try {
                // when
                var reading = executor.submit(() -> boardService.getAll(
                        owner.getId(), PROJECT_CODE, owner.getId(), FIRST_PAGE, PAGE_SIZE, completed));
                await(listingRead);
                transaction.executeWithoutResult(status -> {
                    Card changing = entityManager.find(Card.class, card.getId());
                    changing.move(changing.getBoard(), completed ? CardStatus.IN_PROGRESS : CardStatus.DONE, CARD_POSITION);
                });
                changeCommitted.countDown();
                BoardSliceResponse response = reading.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                // then
                assertThat(response.items()).extracting(BoardProgressResponse::id).containsExactly(board.getId());
                assertThat(response.items().getFirst().completionRate())
                        .isEqualTo(completed ? COMPLETE_RATE : INCOMPLETE_RATE);
                assertThat(response.hasNext()).isFalse();
                BoardSliceResponse previousGroup = boardService.getAll(
                        owner.getId(), PROJECT_CODE, owner.getId(), FIRST_PAGE, PAGE_SIZE, completed);
                BoardSliceResponse changedGroup = boardService.getAll(
                        owner.getId(), PROJECT_CODE, owner.getId(), FIRST_PAGE, PAGE_SIZE, !completed);
                assertThat(previousGroup.items()).isEmpty();
                assertThat(changedGroup.items()).extracting(BoardProgressResponse::id).containsExactly(board.getId());
                assertThat(changedGroup.items().getFirst().completionRate())
                        .isEqualTo(completed ? INCOMPLETE_RATE : COMPLETE_RATE);
            } finally {
                changeCommitted.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    /**
     * 다른 스레드에서 조회할 수 있도록 사용자, 멤버, 보드와 카드를 사전에 커밋한다.
     */
    private void prepareData(CardStatus initialStatus) {
        transaction.executeWithoutResult(status -> {
            owner = User.create("생성자", "board-progress-concurrency@example.com", null);
            entityManager.persist(owner);
            project = Project.create(owner, PROJECT_CODE, "완료율 동시성", null);
            entityManager.persist(project);
            Member member = Member.create(owner, project, MemberRole.OWNER);
            entityManager.persist(member);
            board = project.addBoard("보드");
            entityManager.persist(board);
            card = Card.create(owner, "담당자", "카드", "내용", initialStatus,
                    member, project, board, null, null);
            entityManager.persist(card);
        });
    }

    /**
     * 작업 스레드 종료 후 프로젝트 연쇄 삭제로 하위 데이터를 정리하고 사용자를 삭제한다.
     */
    @AfterEach
    void 테스트_데이터를_정리한다() {
        if (owner == null) {
            return;
        }
        transaction.executeWithoutResult(status -> {
            Project remainingProject = project == null ? null : entityManager.find(Project.class, project.getId());
            if (remainingProject != null) {
                entityManager.remove(remainingProject);
                entityManager.flush();
            }
            User remainingOwner = entityManager.find(User.class, owner.getId());
            if (remainingOwner != null) {
                entityManager.remove(remainingOwner);
            }
        });
    }

    /**
     * 스레드 간 동기화에 시간 제한을 두어 실패 시 무한 대기를 방지한다.
     */
    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("완료율 동시성 테스트 대기가 중단되었습니다.", exception);
        }
    }
}
