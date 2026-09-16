package work.managerbe.board.dto;

import org.junit.jupiter.api.Test;
import work.managerbe.board.domain.Board;
import work.managerbe.project.domain.Project;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mockito로 영속화된 엔티티 값을 제공해 응답 필드 변환과 프로젝트가 없는 경우를 검증한다.
 */
class BoardResponseTest {

    private static final Long BOARD_ID = 10L;
    private static final Long PROJECT_ID = 20L;
    private static final String BOARD_NAME = "진행 중";
    private static final int SORT_ORDER = 2;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 10, 0);
    private static final LocalDateTime UPDATED_AT = CREATED_AT.plusDays(1);

    @Test
    void 보드의_모든_속성과_프로젝트_ID를_응답으로_변환한다() {
        // given
        Project project = mock(Project.class);
        Board board = mock(Board.class);
        when(project.getId()).thenReturn(PROJECT_ID);
        when(board.getId()).thenReturn(BOARD_ID);
        when(board.getProject()).thenReturn(project);
        when(board.getName()).thenReturn(BOARD_NAME);
        when(board.getSortOrder()).thenReturn(SORT_ORDER);
        when(board.getCreatedAt()).thenReturn(CREATED_AT);
        when(board.getUpdatedAt()).thenReturn(UPDATED_AT);

        // when
        BoardResponse response = BoardResponse.from(board);

        // then
        assertThat(response.id()).isEqualTo(BOARD_ID);
        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.name()).isEqualTo(BOARD_NAME);
        assertThat(response.sortOrder()).isEqualTo(SORT_ORDER);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void 프로젝트가_없는_보드도_응답으로_변환한다() {
        // given
        Board board = mock(Board.class);
        when(board.getId()).thenReturn(null);
        when(board.getName()).thenReturn(BOARD_NAME);
        when(board.getSortOrder()).thenReturn(SORT_ORDER);

        // when
        BoardResponse response = BoardResponse.from(board);

        // then
        assertThat(response.projectId()).isNull();
        assertThat(response.id()).isNull();
        assertThat(response.name()).isEqualTo(BOARD_NAME);
        assertThat(response.sortOrder()).isEqualTo(SORT_ORDER);
        assertThat(response.createdAt()).isNull();
        assertThat(response.updatedAt()).isNull();
    }
}
