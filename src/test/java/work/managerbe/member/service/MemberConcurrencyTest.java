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
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import work.managerbe.global.exception.member.MemberErrorCode;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.member.domain.Member;
import work.managerbe.member.domain.MemberRole;
import work.managerbe.member.dto.request.MemberCreateRequest;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 독립 트랜잭션의 중복 가입을 겹쳐 프로젝트 잠금 대기와 도메인 오류, 단일 저장을 검증한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-concurrency-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class MemberConcurrencyTest {

    private static final int WORKER_COUNT = 2;
    private static final int COMPLETION_TIMEOUT_SECONDS = 10;
    private static final int LOCK_WAIT_MILLIS = 500;

    private final EntityManager entityManager;
    private final MemberService memberService;
    private final TransactionTemplate transaction;

    @Autowired
    MemberConcurrencyTest(EntityManager entityManager, MemberService memberService,
                          PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.memberService = memberService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 첫 가입의 커밋을 지연해 두 번째 가입의 대기와 커밋 후 409 중복 오류 및 저장 결과를 확인한다. */
    @Test
    void 동시_중복_가입은_잠금을_기다린_뒤_중복_오류를_반환하고_한_멤버만_저장한다() throws Exception {
        // given
        User creator = User.create("생성자", "member-concurrency-owner@test.com", null);
        User target = User.create("대상", "member-concurrency-target@test.com", null);
        Project project = Project.create(creator, "CONCURRENT", "동시 가입", null);
        transaction.executeWithoutResult(status -> {
            entityManager.persist(creator);
            entityManager.persist(target);
            entityManager.persist(project);
            entityManager.persist(Member.create(creator, project, MemberRole.OWNER));
        });
        var request = new MemberCreateRequest(target.getId(), MemberRole.MEMBER);
        var firstCreated = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var allowFirstCommit = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(WORKER_COUNT)) {
            try {
                // when
                var first = executor.submit(() -> transaction.execute(status -> {
                    var response = memberService.create(creator.getId(), project.getCode(), creator.getId(), request);
                    firstCreated.countDown();
                    await(allowFirstCommit);
                    return response;
                }));
                await(firstCreated);
                var second = executor.submit(() -> transaction.execute(status -> {
                    secondStarted.countDown();
                    return memberService.create(creator.getId(), project.getCode(), creator.getId(), request);
                }));
                await(secondStarted);

                // then
                assertThatThrownBy(() -> second.get(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                allowFirstCommit.countDown();
                var firstResponse = first.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertThat(firstResponse.userId()).isEqualTo(target.getId());
                assertThatThrownBy(() -> second.get(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .cause().isInstanceOfSatisfying(MemberException.class, error -> {
                            assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_ALREADY_EXISTS);
                            assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                        });
                transaction.executeWithoutResult(status -> {
                    var members = entityManager.createQuery(
                                    "select m from Member m where m.project.id = :projectId and m.user.id = :userId",
                                    Member.class)
                            .setParameter("projectId", project.getId())
                            .setParameter("userId", target.getId()).getResultList();
                    assertThat(members).extracting(Member::getId).containsExactly(firstResponse.id());
                    assertThat(members).extracting(Member::getRole).containsExactly(MemberRole.MEMBER);
                });
            } finally {
                allowFirstCommit.countDown();
                executor.shutdownNow();
            }
        } finally {
            transaction.executeWithoutResult(status -> {
                entityManager.createQuery("delete from Member m where m.project.id = :projectId")
                        .setParameter("projectId", project.getId()).executeUpdate();
                entityManager.remove(entityManager.find(Project.class, project.getId()));
                entityManager.remove(entityManager.find(User.class, target.getId()));
                entityManager.remove(entityManager.find(User.class, creator.getId()));
            });
        }
    }

    /** 작업 스레드가 실패하거나 중단되면 제한 시간 내에 테스트를 실패시킨다. */
    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(COMPLETION_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시 가입 테스트 대기가 중단되었습니다.", exception);
        }
    }
}
