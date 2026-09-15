package work.managerbe.board.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.service.BoardService;
import work.managerbe.user.domain.User;
import work.managerbe.project.domain.Project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2에 Flyway 스키마를 적용하고 영속성 컨텍스트를 비워 실제 저장, 조회, 삭제와 필수 관계를 검증한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:board-repository-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Transactional
class BoardRepositoryTest {

    private static final String BOARD_NAME = "진행 중";
    private static final int SORT_ORDER = 2;

    private final BoardRepository boardRepository;
    private final EntityManager entityManager;
    private final BoardService boardService;

    @Autowired
    BoardRepositoryTest(BoardRepository boardRepository, EntityManager entityManager, BoardService boardService) {
        this.boardRepository = boardRepository;
        this.entityManager = entityManager;
        this.boardService = boardService;
    }

    @Test
    void 저장한_보드를_프로젝트와_감사_시각을_포함해_조회한다() {
        // given
        Project project = Project.create("TEST", "테스트 프로젝트", null);
        entityManager.persist(project);
        Board board = Board.create(BOARD_NAME, SORT_ORDER, project);

        // when
        Board saved = boardRepository.saveAndFlush(board);
        entityManager.clear();
        Board found = boardRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found).isNotSameAs(saved);
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo(BOARD_NAME);
        assertThat(found.getSortOrder()).isEqualTo(SORT_ORDER);
        assertThat(found.getProject().getId()).isEqualTo(project.getId());
        assertThat(found.getProject().getName()).isEqualTo(project.getName());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 보드를_삭제하면_조회되지_않고_프로젝트는_유지된다() {
        // given
        Project project = Project.create("TEST", "테스트 프로젝트", null);
        entityManager.persist(project);
        Board board = boardRepository.saveAndFlush(Board.create(BOARD_NAME, SORT_ORDER, project));
        entityManager.clear();

        // when
        boardRepository.deleteById(board.getId());
        boardRepository.flush();
        entityManager.clear();

        // then
        assertThat(boardRepository.findById(board.getId())).isEmpty();
        assertThat(entityManager.find(Project.class, project.getId())).isNotNull();
    }

    @Test
    void 서비스를_통해_생성하면_순서와_감사_시각이_저장된다() {
        // given
        User user = User.create("작성자", "board@example.com", null);
        Project project = Project.create("SERVICE", "프로젝트", null);
        entityManager.persist(user);
        entityManager.persist(project);

        // when
        var first = boardService.create(user.getId(), project.getId(), new BoardCreateRequest("첫 보드"));
        var second = boardService.create(user.getId(), project.getId(), new BoardCreateRequest("둘째 보드"));
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(first.sortOrder()).isEqualTo(1);
        assertThat(second.sortOrder()).isEqualTo(2);
        assertThat(second.projectId()).isEqualTo(project.getId());
        assertThat(second.createdAt()).isNotNull();
        assertThat(second.updatedAt()).isNotNull();
        Board saved = boardRepository.findById(second.id()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("둘째 보드");
        assertThat(saved.getSortOrder()).isEqualTo(2);
    }

    @Test
    void 보드가_없는_프로젝트의_최대_순서는_0이다() {
        // given
        Project project = Project.create("EMPTY", "빈 프로젝트", null);
        entityManager.persist(project);

        // when
        int maximum = boardRepository.findMaxSortOrderByProjectId(project.getId());

        // then
        assertThat(maximum).isZero();
    }

    @Test
    void 다른_프로젝트를_제외하고_보드_개수가_아닌_최대_순서를_조회한다() {
        // given
        Project project = Project.create("TARGET", "대상", null);
        Project other = Project.create("OTHER", "다른 프로젝트", null);
        entityManager.persist(project);
        entityManager.persist(other);
        boardRepository.save(Board.create("첫 보드", 1, project));
        boardRepository.save(Board.create("마지막 보드", 5, project));
        boardRepository.save(Board.create("다른 보드", 10, other));
        entityManager.flush();
        entityManager.clear();

        // when
        int maximum = boardRepository.findMaxSortOrderByProjectId(project.getId());

        // then
        assertThat(maximum).isEqualTo(5);
    }

    @Test
    void 프로젝트가_없는_보드는_저장할_수_없다() {
        // given
        Board board = Board.create(BOARD_NAME, SORT_ORDER, null);

        // when / then
        assertThatThrownBy(() -> boardRepository.saveAndFlush(board))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
