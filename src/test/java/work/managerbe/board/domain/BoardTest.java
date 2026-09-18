package work.managerbe.board.domain;

import work.managerbe.user.domain.User;
import org.junit.jupiter.api.Test;
import java.util.List;
import work.managerbe.global.exception.board.BoardErrorCode;
import work.managerbe.global.exception.board.BoardException;
import work.managerbe.project.domain.Project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 정적 팩터리로 생성한 보드의 속성과 영속화 전 상태를 검증한다.
 */
class BoardTest {

    private static final String BOARD_NAME = "진행 중";

    /**
     * 대용량 목록을 할당하지 않고 크기를 모킹하여 생성 경계와 등록 여부를 검증한다.
     */
    @Test
    void 목록_크기가_최댓값이면_보드_생성과_등록을_거절한다() {
        // given
        Project project = mock(Project.class);
        List<Board> boards = mock();
        when(project.getBoards()).thenReturn(boards);
        when(boards.size()).thenReturn(Integer.MAX_VALUE);

        // when / then
        assertThatThrownBy(() -> Board.create(BOARD_NAME, project))
                .isInstanceOfSatisfying(BoardException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_SORT_ORDER_EXHAUSTED));
        verify(project, never()).registerBoard(any());
    }

    /**
     * 목록 크기가 최댓값 직전이면 마지막으로 표현 가능한 목록 위치에 보드를 생성한다.
     */
    @Test
    void 목록_크기가_최댓값_직전이면_보드를_생성한다() {
        // given
        Project project = mock(Project.class);
        List<Board> boards = mock();
        when(project.getBoards()).thenReturn(boards);
        when(boards.size()).thenReturn(Integer.MAX_VALUE - 1);

        // when
        Board board = Board.create(BOARD_NAME, project);

        // then
        assertThat(board.getSortOrder()).isEqualTo(Integer.MAX_VALUE - 1);
        verify(project).registerBoard(board);
    }

    @Test
    void 프로젝트_목록에_등록한_순서로_보드를_생성한다() {
        // given
        Project project = Project.create(User.create("생성자", "creator@example.com", null), "TEST", "테스트 프로젝트", null);

        // when
        Board board = Board.create(BOARD_NAME, project);

        // then
        assertThat(board.getName()).isEqualTo(BOARD_NAME);
        assertThat(board.getSortOrder()).isZero();
        assertThat(project.getBoards()).containsExactly(board);
        assertThat(board.getProject()).isSameAs(project);
        assertThat(board.getId()).isNull();
        assertThat(board.getCreatedAt()).isNull();
        assertThat(board.getUpdatedAt()).isNull();
    }

    /**
     * 프로젝트가 없는 생성 경로는 영속화 전에 차단한다.
     */
    @Test
    void 프로젝트_없이_보드를_생성할_수_없다() {
        // given / when / then
        assertThatThrownBy(() -> Board.create(BOARD_NAME, null)).isInstanceOf(NullPointerException.class);
    }

    /**
     * 중복 등록은 무시하고 다른 프로젝트나 null 보드 등록은 거절한다.
     */
    @Test
    void 보드_등록은_프로젝트_일치와_중복을_검증한다() {
        // given
        Project project = Project.create(User.create("생성자", "creator@example.com", null), "TEST", "프로젝트", null);
        Project other = Project.create(User.create("생성자", "creator@example.com", null), "OTHER", "다른 프로젝트", null);
        Board board = Board.create(BOARD_NAME, project);

        // when
        project.registerBoard(board);

        // then
        assertThat(project.getBoards()).containsExactly(board);
        assertThatThrownBy(() -> other.registerBoard(board)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> project.registerBoard(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(other.getBoards()).isEmpty();
    }
}
