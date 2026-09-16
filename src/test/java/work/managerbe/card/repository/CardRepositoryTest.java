package work.managerbe.card.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.card.domain.Card;
import work.managerbe.member.domain.Member;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 스키마의 필수 user_id를 SQL로 채운 뒤 실제 카드 조회와 삭제를 검증한다.
 * Card에 user_id 매핑이 없어 이 테스트는 JPA 저장 동작을 검증하지 않는다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:card-repository-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Transactional
class CardRepositoryTest {

    private static final Long CARD_ID = 100L;
    private static final String USERNAME = "담당자";
    private static final String TITLE = "카드 제목";
    private static final String CONTENT = "카드 내용";
    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 15);
    private static final LocalDate END_DATE = START_DATE.plusDays(3);

    private final CardRepository cardRepository;
    private final EntityManager entityManager;
    private User user;
    private Project project;
    private Member member;
    private Board board;

    @Autowired
    CardRepositoryTest(CardRepository cardRepository, EntityManager entityManager) {
        this.cardRepository = cardRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void 카드와_연관_데이터를_준비한다() {
        user = User.create(USERNAME, "test@example.com", null);
        project = Project.create("TEST", "테스트 프로젝트", null);
        entityManager.persist(user);
        entityManager.persist(project);
        member = Member.create(user, project, "MEMBER");
        board = Board.create("진행 중", project);
        entityManager.persist(member);
        entityManager.persist(board);
        entityManager.flush();
        entityManager.createNativeQuery("""
                INSERT INTO cards (id, user_id, member_id, project_id, board_id,
                    username, title, content, start_date, end_date, created_at, updated_at)
                VALUES (:id, :userId, :memberId, :projectId, :boardId,
                    :username, :title, :content, :startDate, :endDate, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)
                .setParameter("id", CARD_ID)
                .setParameter("userId", user.getId())
                .setParameter("memberId", member.getId())
                .setParameter("projectId", project.getId())
                .setParameter("boardId", board.getId())
                .setParameter("username", USERNAME)
                .setParameter("title", TITLE)
                .setParameter("content", CONTENT)
                .setParameter("startDate", START_DATE)
                .setParameter("endDate", END_DATE)
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    void 카드를_연관_엔티티와_일정_및_감사_시각과_함께_조회한다() {
        // given
        Long cardId = CARD_ID;

        // when
        Card found = cardRepository.findById(cardId).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(CARD_ID);
        assertThat(found.getUsername()).isEqualTo(USERNAME);
        assertThat(found.getTitle()).isEqualTo(TITLE);
        assertThat(found.getContent()).isEqualTo(CONTENT);
        assertThat(found.getMember().getId()).isEqualTo(member.getId());
        assertThat(found.getMember().getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getProject().getId()).isEqualTo(project.getId());
        assertThat(found.getProject().getName()).isEqualTo(project.getName());
        assertThat(found.getBoard().getId()).isEqualTo(board.getId());
        assertThat(found.getBoard().getName()).isEqualTo(board.getName());
        assertThat(found.getStartDate()).isEqualTo(START_DATE);
        assertThat(found.getEndDate()).isEqualTo(END_DATE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 카드를_삭제하면_조회되지_않고_연관_엔티티는_유지된다() {
        // given
        Long cardId = CARD_ID;

        // when
        cardRepository.deleteById(cardId);
        cardRepository.flush();
        entityManager.clear();

        // then
        assertThat(cardRepository.findById(cardId)).isEmpty();
        assertThat(entityManager.find(User.class, user.getId())).isNotNull();
        assertThat(entityManager.find(Member.class, member.getId())).isNotNull();
        assertThat(entityManager.find(Project.class, project.getId())).isNotNull();
        assertThat(entityManager.find(Board.class, board.getId())).isNotNull();
    }
}
