package work.managerbe.board.repository;

import jakarta.persistence.EntityManager;
import work.managerbe.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.PersistenceException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.dto.BoardResponse;
import work.managerbe.board.service.BoardService;
import work.managerbe.user.domain.User;
import work.managerbe.project.domain.Project;
import work.managerbe.project.service.ProjectService;
import work.managerbe.project.dto.request.ProjectCreateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2에 Flyway 스키마를 적용하고 코드 기반 보드 생성과 실제 저장, 조회, 삭제 및 필수 관계를 검증한다.
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

    private final BoardRepository boardRepository;
    private final EntityManager entityManager;
    private final BoardService boardService;
    private final ProjectService projectService;

    @Autowired
    BoardRepositoryTest(BoardRepository boardRepository, EntityManager entityManager, BoardService boardService,
                        ProjectService projectService) {
        this.boardRepository = boardRepository;
        this.entityManager = entityManager;
        this.boardService = boardService;
        this.projectService = projectService;
    }

    /**
     * 프로젝트 생성 서비스가 등록한 활성 멤버로 별도 가입 없이 첫 보드를 저장할 수 있는지 검증한다.
     */
    @Test
    void 프로젝트_생성자는_즉시_보드를_생성할_수_있다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        entityManager.persist(creator);
        var project = projectService.create(creator.getId(),
                new ProjectCreateRequest("CREATOR", "프로젝트", null));
        entityManager.flush();
        entityManager.clear();

        // when
        var board = boardService.create(creator.getId(), project.code(), new BoardCreateRequest(BOARD_NAME));
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(boardRepository.findById(board.id())).isPresent();
        assertThat(board.projectId()).isEqualTo(project.id());
        assertThat(board.sortOrder()).isZero();
        Member member = entityManager.createQuery(
                        "select m from Member m where m.user.id = :userId and m.project.id = :projectId", Member.class)
                .setParameter("userId", creator.getId())
                .setParameter("projectId", project.id())
                .getSingleResult();
        assertThat(member.getRole()).isEqualTo("OWNER");
        assertThat(entityManager.find(Project.class, project.id()).getCreator().getId())
                .isEqualTo(creator.getId());
        assertThat(member.getJoinedAt()).isNotNull();
        assertThat(member.getLeftAt()).isNull();
    }

    @Test
    void 저장한_보드를_프로젝트와_감사_시각을_포함해_조회한다() {
        // given
        Project project = Project.create(creator(), "TEST", "테스트 프로젝트", null);
        entityManager.persist(project);
        Board board = project.addBoard(BOARD_NAME);

        // when
        Board saved = boardRepository.saveAndFlush(board);
        entityManager.clear();
        Board found = boardRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found).isNotSameAs(saved);
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo(BOARD_NAME);
        assertThat(found.getSortOrder()).isZero();
        assertThat(found.getProject().getId()).isEqualTo(project.getId());
        assertThat(found.getProject().getName()).isEqualTo(project.getName());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 보드를_삭제하면_조회되지_않고_프로젝트는_유지된다() {
        // given
        Project project = Project.create(creator(), "TEST", "테스트 프로젝트", null);
        entityManager.persist(project);
        Board board = boardRepository.saveAndFlush(project.addBoard(BOARD_NAME));
        entityManager.clear();

        // when
        boardRepository.deleteById(board.getId());
        boardRepository.flush();
        entityManager.clear();

        // then
        assertThat(boardRepository.findById(board.getId())).isEmpty();
        assertThat(entityManager.find(Project.class, project.getId())).isNotNull();
    }

    /**
     * 컨텍스트를 비운 뒤 목록과 각 보드의 순서를 확인해 DB 기본값에 머무르지 않는지 검증한다.
     */
    @Test
    void 서비스를_통해_생성하면_순서와_감사_시각이_저장된다() {
        // given
        User user = User.create("작성자", "board@example.com", null);
        Project project = Project.create(creator(), "SERVICE", "프로젝트", null);
        entityManager.persist(user);
        entityManager.persist(project);
        entityManager.persist(Member.create(user, project, "MEMBER"));

        // when
        var first = boardService.create(user.getId(), project.getCode(), new BoardCreateRequest("첫 보드"));
        var second = boardService.create(user.getId(), project.getCode(), new BoardCreateRequest("둘째 보드"));
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(first.sortOrder()).isZero();
        assertThat(second.sortOrder()).isEqualTo(1);
        assertThat(second.projectId()).isEqualTo(project.getId());
        assertThat(second.createdAt()).isNotNull();
        assertThat(second.updatedAt()).isNotNull();
        Board saved = boardRepository.findById(second.id()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("둘째 보드");
        assertThat(saved.getSortOrder()).isEqualTo(1);
        Project loaded = entityManager.find(Project.class, project.getId());
        assertThat(loaded.getBoards()).extracting(Board::getName).containsExactly("첫 보드", "둘째 보드");
        assertThat(loaded.getBoards()).extracting(Board::getSortOrder).containsExactly(0, 1);
        var third = boardService.create(user.getId(), project.getCode(), new BoardCreateRequest("셋째 보드"));
        entityManager.flush();
        entityManager.clear();
        assertThat(third.sortOrder()).isEqualTo(2);
        assertThat(boardRepository.findById(third.id()).orElseThrow().getSortOrder()).isEqualTo(2);
        assertThat(entityManager.find(Project.class, project.getId()).getBoards())
                .extracting(Board::getName).containsExactly("첫 보드", "둘째 보드", "셋째 보드");
        assertThat(entityManager.find(Project.class, project.getId()).getBoards())
                .extracting(Board::getSortOrder).containsExactly(0, 1, 2);
    }

    @Test
    void 프로젝트가_없는_보드는_저장할_수_없다() {
        // given
        String insertWithoutProject = """
                INSERT INTO boards (name, sort_order, created_at, updated_at)
                VALUES ('보드', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        // when / then
        assertThatThrownBy(() -> entityManager.createNativeQuery(insertWithoutProject).executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    /**
     * 팩터리 직접 호출과 프로젝트 생성 API를 섞어도 DB 순서와 목록이 일치하는지 검증한다.
     */
    @Test
    void 팩터리로_직접_생성한_보드도_목록_순서대로_저장된다() {
        // given
        Project project = Project.create(creator(), "FACTORY", "팩터리", null);
        entityManager.persist(project);
        Board first = Board.create("첫 보드", project);
        Board second = project.addBoard("둘째 보드");
        Board third = Board.create("셋째 보드", project);

        // when
        boardRepository.save(first);
        boardRepository.save(second);
        boardRepository.saveAndFlush(third);
        entityManager.clear();

        // then
        assertThat(boardRepository.findById(second.getId()).orElseThrow().getSortOrder()).isEqualTo(1);
        assertThat(boardRepository.findById(third.getId()).orElseThrow().getSortOrder()).isEqualTo(2);
        assertThat(entityManager.find(Project.class, project.getId()).getBoards())
                .extracting(Board::getId).containsExactly(first.getId(), second.getId(), third.getId());
    }

    /**
     * 다른 프로젝트의 활성 멤버이거나 다른 사용자가 대상 프로젝트 멤버여도 생성할 수 없다.
     */
    @Test
    void 대상_프로젝트에_참여하지_않은_사용자는_생성을_거절한다() {
        // given
        User user = User.create("요청자", "outsider@example.com", null);
        User member = User.create("멤버", "member@example.com", null);
        Project target = Project.create(creator(), "TARGET", "대상", null);
        Project other = Project.create(creator(), "OTHER", "다른 프로젝트", null);
        entityManager.persist(user);
        entityManager.persist(member);
        entityManager.persist(target);
        entityManager.persist(other);
        entityManager.persist(Member.create(user, other, "MEMBER"));
        entityManager.persist(Member.create(member, target, "MEMBER"));
        entityManager.flush();
        entityManager.clear();

        // when / then
        assertThatThrownBy(() -> boardService.create(user.getId(), target.getCode(), new BoardCreateRequest("보드")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(boardRepository.count()).isZero();
    }

    /**
     * 실제 탈퇴 시각이 저장된 멤버를 생성 권한 대상에서 제외한다.
     */
    @Test
    void 탈퇴한_멤버는_보드_생성을_거절한다() {
        // given
        User user = User.create("탈퇴자", "left@example.com", null);
        Project project = Project.create(creator(), "LEFT", "탈퇴한 프로젝트", null);
        entityManager.persist(user);
        entityManager.persist(project);
        Member member = Member.create(user, project, "MEMBER");
        entityManager.persist(member);
        member.leave(member.getJoinedAt());
        entityManager.flush();
        entityManager.clear();

        // when / then
        assertThatThrownBy(() -> boardService.create(user.getId(), project.getCode(), new BoardCreateRequest("보드")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(boardRepository.count()).isZero();
    }

    /**
     * 대상 프로젝트만 정렬해 조회하고 첫 슬라이스, 마지막 및 범위 밖 슬라이스의 다음 데이터 여부를 검증한다.
     */
    @Test
    void 코드로_보드_목록을_정렬하고_페이지로_조회한다() {
        // given
        User user = User.create("조회자", "board-list@example.com", null);
        Project project = Project.create(creator(), "LIST", "목록", null);
        Project other = Project.create(creator(), "OTHER_LIST", "다른 프로젝트", null);
        entityManager.persist(user);
        entityManager.persist(project);
        entityManager.persist(other);
        entityManager.persist(Member.create(user, project, "MEMBER"));
        Board first = boardRepository.save(project.addBoard("첫 보드"));
        Board second = boardRepository.save(project.addBoard("둘째 보드"));
        Board third = boardRepository.save(project.addBoard("셋째 보드"));
        boardRepository.save(other.addBoard("제외할 보드"));
        entityManager.flush();
        entityManager.clear();

        // when
        var firstPage = boardService.getAll(user.getId(), project.getCode(), 0, 2);
        var secondPage = boardService.getAll(user.getId(), project.getCode(), 1, 2);
        var outsidePage = boardService.getAll(user.getId(), project.getCode(), 2, 2);

        // then
        assertThat(firstPage.items()).extracting(BoardResponse::id)
                .containsExactly(first.getId(), second.getId());
        assertThat(firstPage.items()).extracting(BoardResponse::sortOrder)
                .containsExactly(0, 1);
        assertThat(firstPage.items()).extracting(BoardResponse::projectId)
                .containsOnly(project.getId());
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.items()).extracting(BoardResponse::id)
                .containsExactly(third.getId());
        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(outsidePage.items()).isEmpty();
        assertThat(outsidePage.hasNext()).isFalse();
    }

    @Test
    void 보드가_없는_프로젝트는_빈_페이지를_반환한다() {
        // given
        User user = User.create("조회자", "empty-list@example.com", null);
        Project project = Project.create(creator(), "EMPTY_LIST", "빈 목록", null);
        entityManager.persist(user);
        entityManager.persist(project);
        entityManager.persist(Member.create(user, project, "MEMBER"));
        entityManager.flush();
        entityManager.clear();

        // when
        var response = boardService.getAll(user.getId(), project.getCode(), 0, 20);

        // then
        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 탈퇴한_멤버는_보드_목록_조회를_거절한다() {
        // given
        User user = User.create("탈퇴자", "left-list@example.com", null);
        Project project = Project.create(creator(), "LEFT_LIST", "목록", null);
        entityManager.persist(user);
        entityManager.persist(project);
        Member member = Member.create(user, project, "MEMBER");
        entityManager.persist(member);
        member.leave(member.getJoinedAt());
        entityManager.flush();
        entityManager.clear();

        // when / then
        assertThatThrownBy(() -> boardService.getAll(user.getId(), project.getCode(), 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * 프로젝트의 필수 생성자 관계를 위한 사용자를 저장한다.
     */
    private User creator() {
        User creator = User.create("생성자", "creator@example.com", null);
        entityManager.persist(creator);
        return creator;
    }
}
