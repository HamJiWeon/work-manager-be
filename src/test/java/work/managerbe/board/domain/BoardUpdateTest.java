package work.managerbe.board.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;
import work.managerbe.global.exception.board.BoardException;
import work.managerbe.global.exception.board.BoardErrorCode;
import static org.assertj.core.api.Assertions.*;

/**
 * 보드 이름과 목록 위치 동기화의 입력 검증 및 상태 변경을 검증한다.
 */
class BoardUpdateTest {
    /**
     * 잘못된 이름으로 직접 변경해도 기존 이름과 감사 시각을 보존한다.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 잘못된_이름은_변경하지_않는다(String name) {
        // given
        Project project = Project.create(User.create("생성자", "name@example.com", null), "WORK", "프로젝트", null);
        Board board = project.addBoard("보드");
        board.rename("기존 이름");
        var updatedAt = board.getUpdatedAt();

        // when / then
        assertThatThrownBy(() -> board.rename(name))
                .isInstanceOfSatisfying(BoardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_NAME));
        assertThat(board.getName()).isEqualTo("기존 이름");
        assertThat(board.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void 동일_이름으로_변경하면_감사_시각을_유지한다() {
        // given
        Project project = Project.create(User.create("생성자", "rename@example.com", null), "WORK", "프로젝트", null);
        Board board = project.addBoard("보드");
        board.rename("변경 이름");
        var updatedAt = board.getUpdatedAt();

        // when
        board.rename("변경 이름");

        // then
        assertThat(board.getName()).isEqualTo("변경 이름");
        assertThat(updatedAt).isNotNull();
        assertThat(board.getUpdatedAt()).isEqualTo(updatedAt);
    }

    /**
     * 음수, 목록 크기 이상의 위치 및 다른 보드의 위치로 동기화하는 요청을 거절한다.
     */
    @ParameterizedTest
    @ValueSource(ints = {-1, 2, Integer.MAX_VALUE, 1})
    void 잘못된_위치로_동기화하면_순서와_감사_시각을_유지한다(int position) {
        // given
        Project project = Project.create(User.create("생성자", "position@example.com", null), "WORK", "프로젝트", null);
        Board first = project.addBoard("첫 보드");
        Board second = project.addBoard("둘째 보드");
        first.rename("기존 이름");
        var updatedAt = first.getUpdatedAt();

        // when / then
        assertThatThrownBy(() -> first.synchronizeSortOrder(position))
                .isInstanceOfSatisfying(BoardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_NOT_FOUND));
        assertThat(project.getBoards()).containsExactly(first, second);
        assertThat(first.getSortOrder()).isZero();
        assertThat(first.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void 동일_순서로_동기화하면_감사_시각을_유지한다() {
        // given
        Project project = Project.create(User.create("생성자", "sync@example.com", null), "WORK", "프로젝트", null);
        Board board = project.addBoard("보드");
        board.rename("기존 이름");
        var updatedAt = board.getUpdatedAt();

        // when
        board.synchronizeSortOrder(0);

        // then
        assertThat(board.getSortOrder()).isZero();
        assertThat(board.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(project.getBoards()).containsExactly(board);
    }
}
