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
import static org.mockito.Mockito.*;

/**
 * 목록 이동의 범위 검증과 동일 위치 요청이 기존 상태를 보존하는지 검증한다.
 */
class BoardUpdateTest {
    /**
     * 다른 프로젝트의 보드 이동을 거절하고 양쪽 목록과 순서 및 감사 시각을 보존한다.
     */
    @Test
    void 목록에_없는_보드는_이동하지_않는다() {
        // given
        User creator = User.create("생성자", "move@example.com", null);
        Project project = Project.create(creator, "WORK", "대상 프로젝트", null);
        Project other = Project.create(creator, "OTHER", "다른 프로젝트", null);
        Board first = project.addBoard("첫 보드");
        Board second = project.addBoard("둘째 보드");
        Board outsider = other.addBoard("외부 보드");
        first.rename("첫 보드 변경");
        second.rename("둘째 보드 변경");
        outsider.rename("외부 보드 변경");
        var firstUpdatedAt = first.getUpdatedAt();
        var secondUpdatedAt = second.getUpdatedAt();
        var outsiderUpdatedAt = outsider.getUpdatedAt();

        // when / then
        assertThatThrownBy(() -> project.moveBoard(outsider, 1))
                .isInstanceOfSatisfying(BoardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_NOT_FOUND));
        assertThat(project.getBoards()).containsExactly(first, second);
        assertThat(other.getBoards()).containsExactly(outsider);
        assertThat(first.getSortOrder()).isZero();
        assertThat(second.getSortOrder()).isEqualTo(1);
        assertThat(outsider.getSortOrder()).isZero();
        assertThat(first.getUpdatedAt()).isEqualTo(firstUpdatedAt);
        assertThat(second.getUpdatedAt()).isEqualTo(secondUpdatedAt);
        assertThat(outsider.getUpdatedAt()).isEqualTo(outsiderUpdatedAt);
    }

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

    /**
     * 같은 식별자의 다른 엔티티 인스턴스를 전달해도 프로젝트가 관리하는 보드를 이동하는지 검증한다.
     */
    @Test
    void 같은_식별자의_다른_인스턴스로_보드를_이동한다() {
        // given
        Project project = Project.create(User.create("생성자", "identity@example.com", null), "WORK", "프로젝트", null);
        Board first = mock(Board.class);
        Board second = mock(Board.class);
        Board equivalentSecond = mock(Board.class);
        when(first.getProject()).thenReturn(project);
        when(first.getId()).thenReturn(1L);
        when(second.getProject()).thenReturn(project);
        when(second.getId()).thenReturn(2L);
        when(equivalentSecond.getId()).thenReturn(2L);
        project.registerBoard(first);
        project.registerBoard(second);

        // when
        project.moveBoard(equivalentSecond, 0);

        // then
        assertThat(project.getBoards()).containsExactly(second, first);
        verify(second).synchronizeSortOrder(0);
        verify(first).synchronizeSortOrder(1);
        verify(equivalentSecond, never()).synchronizeSortOrder(anyInt());
    }

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
