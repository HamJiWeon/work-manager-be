package work.managerbe.board.service;

import java.util.Optional;
import java.util.List;
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
 * 코드 기반 프로젝트 조회와 ID 기반 멤버 검증, 입력 검증 및 목록 끝에 추가하는 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BoardServiceImplTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PROJECT_ID = 1L;
    private static final String PROJECT_CODE = "TEST";
    @Mock BoardRepository boardRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock MemberRepository memberRepository;
    @InjectMocks BoardServiceImpl service;

    /**
     * 실제 도메인 생성 경로에서 한도 예외가 전파되고 목록 변경과 저장이 발생하지 않는지 검증한다.
     */
    @Test
    void 보드_정렬_순서_한도에_도달하면_저장하지_않는다() {
        // given
        Project project = spy(Project.create(PROJECT_CODE, "프로젝트", null));
        List<Board> boards = mock();
        doReturn(boards).when(project).getBoards();
        when(boards.size()).thenReturn(Integer.MAX_VALUE);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        doReturn(PROJECT_ID).when(project).getId();
        when(projectRepository.findByCodeForUpdate(PROJECT_CODE)).thenReturn(Optional.of(project));
        when(memberRepository.existsByUser_IdAndProject_IdAndLeftAtIsNull(USER_ID, PROJECT_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_CODE, new BoardCreateRequest("보드")))
                .isInstanceOfSatisfying(BoardException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_SORT_ORDER_EXHAUSTED));
        verify(project, never()).registerBoard(any());
        verifyNoInteractions(boardRepository);
    }

    @Test
    void 기존_보드_뒤에_요청한_보드를_추가한다() {
        // given
        Project project = spy(Project.create(PROJECT_CODE, "프로젝트", null));
        Board existing = project.addBoard("기존 보드");
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        doReturn(PROJECT_ID).when(project).getId();
        when(projectRepository.findByCodeForUpdate(PROJECT_CODE)).thenReturn(Optional.of(project));
        when(memberRepository.existsByUser_IdAndProject_IdAndLeftAtIsNull(USER_ID, PROJECT_ID)).thenReturn(true);
        when(boardRepository.save(any(Board.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // when
        var response = service.create(USER_ID, PROJECT_CODE, new BoardCreateRequest("Board API"));
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
        Project project = spy(Project.create(PROJECT_CODE, "프로젝트", null));
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        doReturn(PROJECT_ID).when(project).getId();
        when(projectRepository.findByCodeForUpdate(PROJECT_CODE)).thenReturn(Optional.of(project));

        // when / then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_CODE, new BoardCreateRequest("보드")))
                .isInstanceOf(AccessDeniedException.class);
        verify(memberRepository).existsByUser_IdAndProject_IdAndLeftAtIsNull(USER_ID, PROJECT_ID);
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
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_CODE, request))
                .isInstanceOf(BoardException.class);
        verifyNoInteractions(boardRepository, projectRepository, userRepository);
    }

    @Test
    void 요청이_null이면_조회와_저장_없이_거절한다() {
        // given
        BoardCreateRequest request = null;

        // when & then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_CODE, request))
                .isInstanceOfSatisfying(BoardException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_NAME));
        verifyNoInteractions(userRepository, projectRepository, boardRepository);
    }

    @Test
    void 사용자_ID가_null이면_조회와_저장_없이_거절한다() {
        // given
        var request = new BoardCreateRequest("보드");

        // when & then
        assertThatThrownBy(() -> service.create(null, PROJECT_CODE, request))
                .isInstanceOfSatisfying(UserException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(userRepository, projectRepository, boardRepository);
    }

    /**
     * null, 빈 문자열, 공백 코드는 프로젝트 조회와 멤버 검증 전에 거절한다.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 프로젝트_코드가_비어있으면_프로젝트_조회와_저장_없이_거절한다(String code) {
        // given
        var request = new BoardCreateRequest("보드");
        when(userRepository.existsById(USER_ID)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> service.create(USER_ID, code, request))
                .isInstanceOfSatisfying(ProjectException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND));
        verify(userRepository).existsById(USER_ID);
        verifyNoInteractions(projectRepository, boardRepository, memberRepository);
    }

    @Test
    void 없는_사용자는_거절한다() {
        // given
        var request = new BoardCreateRequest("보드");
        // when & then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_CODE, request))
                .isInstanceOf(UserException.class);
        verifyNoInteractions(boardRepository, projectRepository);
    }

    @Test
    void 없는_프로젝트는_거절한다() {
        // given
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        // when & then
        assertThatThrownBy(() -> service.create(USER_ID, PROJECT_CODE, new BoardCreateRequest("보드")))
                .isInstanceOf(ProjectException.class);
        verifyNoInteractions(boardRepository);
    }

}
