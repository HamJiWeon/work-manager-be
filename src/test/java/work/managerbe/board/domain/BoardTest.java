package work.managerbe.board.domain;

import org.junit.jupiter.api.Test;
import work.managerbe.project.domain.Project;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정적 팩터리로 생성한 보드의 속성과 영속화 전 상태를 검증한다.
 */
class BoardTest {

    private static final String BOARD_NAME = "진행 중";
    private static final int SORT_ORDER = 2;

    @Test
    void 이름과_정렬_순서와_프로젝트로_보드를_생성한다() {
        // given
        Project project = Project.create("TEST", "테스트 프로젝트", "TST", null);

        // when
        Board board = Board.create(BOARD_NAME, SORT_ORDER, project);

        // then
        assertThat(board.getName()).isEqualTo(BOARD_NAME);
        assertThat(board.getSortOrder()).isEqualTo(SORT_ORDER);
        assertThat(board.getProject()).isSameAs(project);
        assertThat(board.getId()).isNull();
        assertThat(board.getCreatedAt()).isNull();
        assertThat(board.getUpdatedAt()).isNull();
    }
}
