package work.managerbe.board.domain;

import org.junit.jupiter.api.Test;
import work.managerbe.project.domain.Project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정적 팩터리로 생성한 보드의 속성과 영속화 전 상태를 검증한다.
 */
class BoardTest {

    private static final String BOARD_NAME = "진행 중";

    @Test
    void 프로젝트_목록에_등록한_순서로_보드를_생성한다() {
        // given
        Project project = Project.create("TEST", "테스트 프로젝트", null);

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
        Project project = Project.create("TEST", "프로젝트", null);
        Project other = Project.create("OTHER", "다른 프로젝트", null);
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
