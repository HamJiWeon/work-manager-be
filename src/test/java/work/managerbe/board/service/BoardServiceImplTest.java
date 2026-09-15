package work.managerbe.board.service;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.managerbe.board.domain.Board;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.repository.BoardRepository;
import work.managerbe.global.board.BoardErrorCode;
import work.managerbe.global.board.BoardException;
import work.managerbe.global.project.ProjectException;
import work.managerbe.global.user.UserException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 저장소를 모킹하여 이름 검증, 조회 실패와 최대 순서 기반 생성을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BoardServiceImplTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PROJECT_ID = 1L;
    @Mock BoardRepository boardRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @InjectMocks BoardServiceImpl service;

    @ParameterizedTest
    @ValueSource(ints = {0, 5})
    void 최대_순서_다음에_요청한_이름으로_생성한다(int maximum) {
        // given
        Project project = Project.create("TEST", "프로젝트", null);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(boardRepository.findMaxSortOrderByProjectId(PROJECT_ID)).thenReturn(maximum);
        when(boardRepository.save(any(Board.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // when
        var response = service.create(USER_ID, PROJECT_ID, new BoardCreateRequest("Board API"));
        // then
        assertThat(response.name()).isEqualTo("Board API");
        assertThat(response.sortOrder()).isEqualTo(maximum + 1);
        var order = inOrder(projectRepository, boardRepository);
        order.verify(projectRepository).findByIdForUpdate(PROJECT_ID);
        order.verify(boardRepository).findMaxSortOrderByProjectId(PROJECT_ID);
        order.verify(boardRepository).save(argThat(board -> board.getProject() == project));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 빈_이름은_저장하지_않는다(String name) {
        // given
        var request = new BoardCreateRequest(name);
        // when & then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_ID, request))
                .isInstanceOf(BoardException.class);
        verifyNoInteractions(boardRepository, projectRepository, userRepository);
    }

    @Test
    void 없는_사용자는_거절한다() {
        // given
        var request = new BoardCreateRequest("보드");
        // when & then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_ID, request))
                .isInstanceOf(UserException.class);
        verifyNoInteractions(boardRepository, projectRepository);
    }

    @Test
    void 없는_프로젝트는_거절한다() {
        // given
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        // when & then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_ID, new BoardCreateRequest("보드")))
                .isInstanceOf(ProjectException.class);
        verifyNoInteractions(boardRepository);
    }

    @Test
    void 정렬_순서가_최댓값이면_저장하지_않는다() {
        // given
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(projectRepository.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(Project.create("TEST", "프로젝트", null)));
        when(boardRepository.findMaxSortOrderByProjectId(PROJECT_ID)).thenReturn(Integer.MAX_VALUE);
        // when & then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_ID, new BoardCreateRequest("보드")))
                .isInstanceOfSatisfying(BoardException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_SORT_ORDER_EXHAUSTED));
        verify(boardRepository, never()).save(any());
    }
}
