package work.managerbe.member.service;

import jakarta.persistence.EntityManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import work.managerbe.board.domain.Board;
import work.managerbe.card.domain.Card;
import work.managerbe.card.domain.CardStatus;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.service.CardService;
import work.managerbe.global.exception.member.MemberErrorCode;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.member.domain.Member;
import work.managerbe.member.domain.MemberRole;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 독립 트랜잭션의 탈퇴와 카드 생성을 겹쳐 탈퇴 후 새 담당자 지정 차단과 기존 카드 보존을 검증한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-delete-concurrency-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class MemberDeletionConcurrencyTest {
    private static final int WORKER_COUNT = 2;
    private static final int COMPLETION_TIMEOUT_SECONDS = 10;
    private static final int LOCK_WAIT_MILLIS = 500;

    private final EntityManager entityManager;
    private final MemberService memberService;
    private final CardService cardService;
    private final TransactionTemplate transaction;

    @Autowired
    MemberDeletionConcurrencyTest(EntityManager entityManager, MemberService memberService, CardService cardService,
                                  PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.memberService = memberService;
        this.cardService = cardService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 탈퇴 트랜잭션의 커밋을 지연해 카드 생성이 잠금을 기다린 뒤 담당자 없음으로 실패하는지 확인한다. */
    @Test
    void 카드_생성은_동시_탈퇴의_커밋을_기다리고_탈퇴자를_새_담당자로_지정하지_못한다() throws Exception {
        // given
        User creator = User.create("소유자", "delete-concurrency-owner@test.com", null);
        User target = User.create("탈퇴자", "delete-concurrency-target@test.com", null);
        Project project = Project.create(creator, "DELETE", "동시 탈퇴", null);
        Member member = Member.create(target, project, MemberRole.MEMBER);
        Board board = Board.create("보드", project);
        Card existing = Card.create(creator, "소유자", "기존 카드", "내용", CardStatus.NOT_STARTED,
                member, project, board, null, null);
        transaction.executeWithoutResult(status -> {
            entityManager.persist(creator);
            entityManager.persist(target);
            entityManager.persist(project);
            entityManager.persist(Member.create(creator, project, MemberRole.OWNER));
            entityManager.persist(member);
            entityManager.persist(board);
            entityManager.persist(existing);
        });
        var deleted = new CountDownLatch(1);
        var createStarted = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        var request = new CardCreateRequest("소유자", "새 카드", "내용", CardStatus.NOT_STARTED,
                member.getId(), project.getId(), board.getId(), null, null);

        try (var executor = Executors.newFixedThreadPool(WORKER_COUNT)) {
            try {
                // when
                var deletion = executor.submit(() -> transaction.executeWithoutResult(status -> {
                    memberService.delete(creator.getId(), project.getCode(), member.getId(), creator.getId());
                    deleted.countDown();
                    await(allowCommit);
                }));
                await(deleted);
                var creation = executor.submit(() -> transaction.execute(status -> {
                    createStarted.countDown();
                    return cardService.create(creator.getId(), project.getCode(), board.getId(), creator.getId(), request);
                }));
                await(createStarted);
                // then
                assertThatThrownBy(() -> creation.get(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                allowCommit.countDown();
                deletion.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertThatThrownBy(() -> creation.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).cause()
                        .isInstanceOfSatisfying(MemberException.class,
                                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
                transaction.executeWithoutResult(status -> {
                    assertThat(entityManager.find(Member.class, member.getId()).getLeftAt()).isNotNull();
                    var cards = entityManager.createQuery("select c from Card c where c.project.id = :projectId", Card.class)
                            .setParameter("projectId", project.getId()).getResultList();
                    assertThat(cards).extracting(Card::getId).containsExactly(existing.getId());
                    assertThat(cards).extracting(card -> card.getMember().getId()).containsExactly(member.getId());
                });
            } finally {
                allowCommit.countDown();
                executor.shutdownNow();
            }
        } finally {
            transaction.executeWithoutResult(status -> {
                entityManager.remove(entityManager.find(Project.class, project.getId()));
                entityManager.flush();
                entityManager.remove(entityManager.find(User.class, target.getId()));
                entityManager.remove(entityManager.find(User.class, creator.getId()));
            });
        }
    }

    /** 스레드 실패나 중단이 무한 대기로 이어지지 않도록 제한 시간 내 신호를 기다린다. */
    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시 탈퇴 테스트 대기가 중단되었습니다.", exception);
        }
    }
}
