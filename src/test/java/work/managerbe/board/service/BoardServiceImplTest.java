package work.managerbe.board.service;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import work.managerbe.member.repository.MemberRepository;
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
import work.managerbe.global.exception.board.BoardException;
import work.managerbe.global.exception.board.BoardErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 저장소를 모킹하여 null 입력과 이름 검증, 조회 실패 및 목록 끝에 추가하는 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BoardServiceImplTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PROJECT_ID = 1L;
    @Mock BoardRepository boardRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock MemberRepository memberRepository;
    @InjectMocks BoardServiceImpl service;

    @Test
    void 기존_보드_뒤에_요청한_보드를_추가한다() {
        // given
        Project project = Project.create("TEST", "프로젝트", null);
        Board existing = project.addBoard("기존 보드");
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(memberRepository.existsByUser_IdAndProject_IdAndLeftAtIsNull(USER_ID, PROJECT_ID)).thenReturn(true);
        when(boardRepository.save(any(Board.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // when
        var response = service.create(USER_ID, PROJECT_ID, new BoardCreateRequest("Board API"));
        // then
        assertThat(response.name()).isEqualTo("Board API");
        assertThat(response.sortOrder()).isEqualTo(1);
        assertThat(project.getBoards()).hasSize(2);
        assertThat(project.getBoards().getFirst()).isSameAs(existing);
        assertThat(project.getBoards().getLast().getName()).isEqualTo("Board API");
        verify(boardRepository).save(project.getBoards().getLast());
    }

    /**
     * 활성 멤버가 아니면 목록 변경과 저장 전에 거절한다.
     */
    @Test
    void 활성_멤버가_아니면_보드를_추가하지_않는다() {
        // given
        Project project = Project.create("TEST", "프로젝트", null);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));

        // when / then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_ID, new BoardCreateRequest("보드")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(project.getBoards()).isEmpty();
        verifyNoInteractions(boardRepository);
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
    void 요청이_null이면_조회와_저장_없이_거절한다() {
        // given
        BoardCreateRequest request = null;

        // when & then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_ID, request))
                .isInstanceOfSatisfying(BoardException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_NAME));
        verifyNoInteractions(userRepository, projectRepository, boardRepository);
    }

    @Test
    void 사용자_ID가_null이면_조회와_저장_없이_거절한다() {
        // given
        var request = new BoardCreateRequest("보드");

        // when & then
        assertThatThrownBy(() -> service.create(null, PROJECT_ID, request))
                .isInstanceOfSatisfying(UserException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(userRepository, projectRepository, boardRepository);
    }

    @Test
    void 프로젝트_ID가_null이면_프로젝트_조회와_저장_없이_거절한다() {
        // given
        var request = new BoardCreateRequest("보드");
        when(userRepository.existsById(USER_ID)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> service.create(USER_ID, null, request))
                .isInstanceOfSatisfying(ProjectException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND));
        verify(userRepository).existsById(USER_ID);
        verifyNoInteractions(projectRepository, boardRepository);
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

}
