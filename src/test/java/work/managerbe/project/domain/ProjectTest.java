package work.managerbe.project.domain;

import org.junit.jupiter.api.Test;
import work.managerbe.board.domain.Board;
import work.managerbe.global.exception.board.BoardErrorCode;
import work.managerbe.global.exception.board.BoardException;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프로젝트 팩터리가 생성자와 코드 및 초기 카드 번호를 보존하는지 검증한다.
 */
class ProjectTest {

    @Test
    void null이거나_이미_제거된_보드는_다시_제거할_수_없다() {
        // given
        Project project = Project.create(User.create("생성자", "remove@example.com", null), "WORK", "프로젝트", null);
        Board board = project.addBoard("기존 보드");
        Board removed = project.addBoard("제거할 보드");
        project.removeBoard(removed);

        // when / then
        assertThatThrownBy(() -> project.removeBoard(null))
                .isInstanceOfSatisfying(BoardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_NOT_FOUND));
        assertThatThrownBy(() -> project.removeBoard(removed))
                .isInstanceOfSatisfying(BoardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_NOT_FOUND));
        assertThat(project.getBoards()).containsExactly(board);
    }

    @Test
    void 다른_프로젝트의_보드는_목록에서_제거할_수_없다() {
        // given
        User creator = User.create("생성자", "other-project@example.com", null);
        Project project = Project.create(creator, "WORK", "프로젝트", null);
        Project other = Project.create(creator, "OTHER", "다른 프로젝트", null);
        Board board = project.addBoard("기존 보드");
        Board otherBoard = other.addBoard("다른 보드");

        // when / then
        assertThatThrownBy(() -> project.removeBoard(otherBoard))
                .isInstanceOfSatisfying(BoardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_NOT_FOUND));
        assertThat(project.getBoards()).containsExactly(board);
        assertThat(other.getBoards()).containsExactly(otherBoard);
    }
    @Test
    void 프로젝트_생성시_생성자와_코드_및_초기값을_설정한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);

        // when
        Project project = Project.create(creator, "WORK", "업무 관리", "설명");

        // then
        assertThat(project.getCreator()).isSameAs(creator);
        assertThat(project.getCode()).isEqualTo("WORK");
        assertThat(project.getName()).isEqualTo("업무 관리");
        assertThat(project.getDescription()).isEqualTo("설명");
        assertThat(project.getNextCardNumber()).isEqualTo(1L);
        assertThat(project.getBoards()).isEmpty();
    }
}
