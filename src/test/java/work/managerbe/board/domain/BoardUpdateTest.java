package work.managerbe.board.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;
import work.managerbe.global.exception.board.BoardException;
import work.managerbe.global.exception.board.BoardErrorCode;
import static org.assertj.core.api.Assertions.*;

/**
 * 목록 이동의 범위 검증과 동일 위치 요청이 기존 상태를 보존하는지 검증한다.
 */
class BoardUpdateTest {
    @ParameterizedTest
    @ValueSource(ints = {-1, 2, Integer.MAX_VALUE})
    void 범위_밖_순서는_목록을_변경하지_않는다(int position) {
        // given
        Project project = Project.create(User.create("생성자", "edit@example.com", null), "WORK", "프로젝트", null);
        Board first = project.addBoard("첫 보드");
        Board second = project.addBoard("둘째 보드");
        // when / then
        assertThatThrownBy(() -> project.moveBoard(first, position))
                .isInstanceOfSatisfying(BoardException.class, e -> assertThat(e.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_SORT_ORDER));
        assertThat(project.getBoards()).containsExactly(first, second);
        assertThat(first.getSortOrder()).isZero();
        assertThat(second.getSortOrder()).isEqualTo(1);
    }

    @Test
    void 동일_위치로_이동하면_순서와_감사_시각을_유지한다() {
        // given
        Project project = Project.create(User.create("생성자", "same@example.com", null), "WORK", "프로젝트", null);
        Board board = project.addBoard("보드");
        var updatedAt = board.getUpdatedAt();
        // when
        project.moveBoard(board, 0);
        // then
        assertThat(project.getBoards()).containsExactly(board);
        assertThat(board.getSortOrder()).isZero();
        assertThat(board.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
