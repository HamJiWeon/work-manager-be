package work.managerbe.card.repository;

import work.managerbe.member.domain.MemberRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.card.domain.Card;
import work.managerbe.card.domain.CardStatus;
import work.managerbe.member.domain.Member;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 스키마에서 기존 SQL 데이터 조회와 카드 JPA 저장 및 삭제를 검증한다.
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
        project = Project.create(user, "TEST", "테스트 프로젝트", null);
        entityManager.persist(user);
        entityManager.persist(project);
        member = Member.create(user, project, MemberRole.MEMBER);
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
    void 카드를_JPA로_저장하면_필수_생성자와_상태가_보존된다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        entityManager.persist(creator);
        Card card = Card.create(creator, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                member, project, board, START_DATE, END_DATE);
        // when
        Long savedId = cardRepository.saveAndFlush(card).getId();
        entityManager.clear();
        Card found = cardRepository.findById(savedId).orElseThrow();
        // then
        assertThat(found.getUser().getId()).isEqualTo(creator.getId());
        assertThat(found.getMember().getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getStatus()).isEqualTo(CardStatus.IN_PROGRESS);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
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

    /** 보드·상태로 조회 범위를 제한하고 위치가 같으면 ID순으로 반환한다. */
    @Test
    void 상태별_카드를_위치와_ID순으로_조회하고_개수를_센다() {
        // given
        Card first = Card.create(user, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                member, project, board, null, null);
        Card second = Card.create(user, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                member, project, board, null, null);
        Card third = Card.create(user, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                member, project, board, null, null);
        first.move(board, CardStatus.IN_PROGRESS, 1);
        second.move(board, CardStatus.IN_PROGRESS, 0);
        third.move(board, CardStatus.IN_PROGRESS, 1);
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.persist(third);
        Board otherBoard = Board.create("다른 보드", project);
        entityManager.persist(otherBoard);
        entityManager.persist(Card.create(user, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                member, project, otherBoard, null, null));
        entityManager.flush();
        Long boardId = board.getId();
        entityManager.clear();
        // when
        var result = cardRepository.findAllByBoardIdAndStatus(
                boardId, CardStatus.IN_PROGRESS);
        // then
        assertThat(result).extracting(Card::getId).containsExactly(second.getId(), first.getId(), third.getId());
        assertThat(cardRepository.countByBoard_IdAndStatus(boardId, CardStatus.IN_PROGRESS)).isEqualTo(3);
        assertThat(cardRepository.findAllByBoardIdAndStatus(boardId, CardStatus.DONE)).isEmpty();
        assertThat(cardRepository.countByBoard_IdAndStatus(boardId, CardStatus.DONE)).isZero();
    }

    /** 벌크 갱신은 대상 구간의 다른 카드만 변경하고 지정 감사 시각을 저장한다. */
    @Test
    void 구간_벌크_갱신은_제외_카드와_다른_상태를_보존한다() {
        // given
        Card excluded = Card.create(user, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                member, project, board, null, null);
        Card shifted = Card.create(user, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                member, project, board, null, null);
        Card outside = Card.create(user, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                member, project, board, null, null);
        shifted.move(board, CardStatus.IN_PROGRESS, 1);
        outside.move(board, CardStatus.IN_PROGRESS, 2);
        entityManager.persist(excluded);
        entityManager.persist(shifted);
        entityManager.persist(outside);
        entityManager.flush();
        var updatedAt = java.time.LocalDateTime.of(2026, 10, 7, 12, 0);
        // when
        int affected = cardRepository.shiftOrder(board.getId(), CardStatus.IN_PROGRESS,
                excluded.getId(), 0, 1, 1, updatedAt);
        entityManager.clear();
        // then
        assertThat(affected).isEqualTo(1);
        assertThat(cardRepository.findById(excluded.getId()).orElseThrow().getSortOrder()).isZero();
        Card reloaded = cardRepository.findById(shifted.getId()).orElseThrow();
        assertThat(reloaded.getSortOrder()).isEqualTo(2);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(cardRepository.findById(outside.getId()).orElseThrow().getSortOrder()).isEqualTo(2);
        assertThat(cardRepository.findById(CARD_ID).orElseThrow().getSortOrder()).isZero();
    }

}
