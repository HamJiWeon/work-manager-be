package work.managerbe.board.service;

import work.managerbe.user.domain.User;
import java.util.Collections;
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
import work.managerbe.board.dto.BoardUpdateItem;
import work.managerbe.board.dto.BoardUpdateRequest;
import work.managerbe.board.repository.BoardRepository;
import work.managerbe.card.repository.CardRepository;
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
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PROJECT_ID = 1L;
    private static final String PROJECT_CODE = "TEST";
    @Mock BoardRepository boardRepository;
    @Mock CardRepository cardRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @Mock MemberRepository memberRepository;
    @InjectMocks BoardServiceImpl service;

    @Test
    void 보드_삭제시_카드를_먼저_삭제하고_목록에서_제거한다() {
        // given
        Project project = spy(Project.create(User.create("생성자", "delete@example.com", null), PROJECT_CODE, "프로젝트", null));
        Board board = mock(Board.class);
        when(board.getProject()).thenReturn(project);
        when(board.getId()).thenReturn(2L);
        project.registerBoard(board);
        doReturn(PROJECT_ID).when(project).getId();
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(projectRepository.findByCreatorIdAndCodeForUpdate(CREATOR_ID, PROJECT_CODE)).thenReturn(Optional.of(project));
        when(memberRepository.existsByUserIdAndProjectId(USER_ID, PROJECT_ID)).thenReturn(true);
        // when
        service.delete(CREATOR_ID, PROJECT_CODE, USER_ID, 2L);
        // then
        assertThat(project.getBoards()).isEmpty();
        var order = inOrder(cardRepository, boardRepository);
        order.verify(cardRepository).deleteAllByBoard_Id(2L);
        order.verify(boardRepository).delete(board);
    }

    @Test
    void 없는_보드는_카드와_보드를_삭제하지_않는다() {
        // given
        Project project = spy(Project.create(User.create("생성자", "missing@example.com", null), PROJECT_CODE, "프로젝트", null));
        doReturn(PROJECT_ID).when(project).getId();
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(projectRepository.findByCreatorIdAndCodeForUpdate(CREATOR_ID, PROJECT_CODE)).thenReturn(Optional.of(project));
        when(memberRepository.existsByUserIdAndProjectId(USER_ID, PROJECT_ID)).thenReturn(true);
        // when / then
        assertThatThrownBy(() -> service.delete(CREATOR_ID, PROJECT_CODE, USER_ID, 2L))
                .isInstanceOfSatisfying(BoardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_NOT_FOUND));
        verifyNoInteractions(cardRepository, boardRepository);
    }

    /**
     * 실제 도메인 생성 경로에서 한도 예외가 전파되고 목록 변경과 저장이 발생하지 않는지 검증한다.
     */
    @Test
    void 보드_정렬_순서_한도에_도달하면_저장하지_않는다() {
        // given
        Project project = spy(Project.create(User.create("생성자", "creator@example.com", null), PROJECT_CODE, "프로젝트", null));
        List<Board> boards = mock();
        doReturn(boards).when(project).getBoards();
        when(boards.size()).thenReturn(Integer.MAX_VALUE);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        doReturn(PROJECT_ID).when(project).getId();
        when(projectRepository.findByCreatorIdAndCodeForUpdate(CREATOR_ID, PROJECT_CODE)).thenReturn(Optional.of(project));
        when(memberRepository.existsByUserIdAndProjectId(USER_ID, PROJECT_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, USER_ID, new BoardCreateRequest("보드")))
                .isInstanceOfSatisfying(BoardException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_SORT_ORDER_EXHAUSTED));
        verify(project, never()).registerBoard(any());
        verifyNoInteractions(boardRepository);
    }

    @Test
    void 기존_보드_뒤에_요청한_보드를_추가한다() {
        // given
        Project project = spy(Project.create(User.create("생성자", "creator@example.com", null), PROJECT_CODE, "프로젝트", null));
        Board existing = project.addBoard("기존 보드");
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        doReturn(PROJECT_ID).when(project).getId();
        when(projectRepository.findByCreatorIdAndCodeForUpdate(CREATOR_ID, PROJECT_CODE)).thenReturn(Optional.of(project));
        when(memberRepository.existsByUserIdAndProjectId(USER_ID, PROJECT_ID)).thenReturn(true);
        when(boardRepository.save(any(Board.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // when
        var response = service.create(CREATOR_ID, PROJECT_CODE, USER_ID, new BoardCreateRequest("Board API"));
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
        Project project = spy(Project.create(User.create("생성자", "creator@example.com", null), PROJECT_CODE, "프로젝트", null));
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        doReturn(PROJECT_ID).when(project).getId();
        when(projectRepository.findByCreatorIdAndCodeForUpdate(CREATOR_ID, PROJECT_CODE)).thenReturn(Optional.of(project));

        // when / then
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, USER_ID, new BoardCreateRequest("보드")))
                .isInstanceOf(AccessDeniedException.class);
        verify(memberRepository).existsByUserIdAndProjectId(USER_ID, PROJECT_ID);
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
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, USER_ID, request))
                .isInstanceOf(BoardException.class);
        verifyNoInteractions(boardRepository, projectRepository, userRepository);
    }

    @Test
    void 요청이_null이면_조회와_저장_없이_거절한다() {
        // given
        BoardCreateRequest request = null;

        // when & then
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, USER_ID, request))
                .isInstanceOfSatisfying(BoardException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_NAME));
        verifyNoInteractions(userRepository, projectRepository, boardRepository);
    }

    @Test
    void 사용자_ID가_null이면_조회와_저장_없이_거절한다() {
        // given
        var request = new BoardCreateRequest("보드");

        // when & then
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, null, request))
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
        assertThatThrownBy(() -> service.create(CREATOR_ID, code, USER_ID, request))
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
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, USER_ID, request))
                .isInstanceOf(UserException.class);
        verifyNoInteractions(boardRepository, projectRepository);
    }

    @Test
    void 없는_프로젝트는_거절한다() {
        // given
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        // when & then
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, USER_ID, new BoardCreateRequest("보드")))
                .isInstanceOf(ProjectException.class);
        verifyNoInteractions(boardRepository);
    }


    /**
     * 활성 멤버가 아니면 보드 조회 전에 거절하고 생성용 잠금을 사용하지 않는다.
     */
    @Test
    void 활성_멤버가_아니면_목록을_조회하지_않는다() {
        // given
        Project project = spy(Project.create(User.create("생성자", "creator@example.com", null), PROJECT_CODE, "프로젝트", null));
        doReturn(PROJECT_ID).when(project).getId();
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(projectRepository.findByCreator_IdAndCode(CREATOR_ID, PROJECT_CODE)).thenReturn(Optional.of(project));

        // when / then
        assertThatThrownBy(() -> service.getAll(CREATOR_ID, PROJECT_CODE, USER_ID, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        verify(memberRepository).existsByUserIdAndProjectId(USER_ID, PROJECT_ID);
        verify(projectRepository, never()).findByCreatorIdAndCodeForUpdate(any(), any());
        verifyNoInteractions(boardRepository);
    }

    @Test
    void 목록_조회시_없는_사용자는_거절한다() {
        // given / when / then
        assertThatThrownBy(() -> service.getAll(CREATOR_ID, PROJECT_CODE, USER_ID, 0, 20))
                .isInstanceOfSatisfying(UserException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(projectRepository, memberRepository, boardRepository);
    }

    @Test
    void 목록_조회시_없는_프로젝트는_거절한다() {
        // given
        when(userRepository.existsById(USER_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.getAll(CREATOR_ID, PROJECT_CODE, USER_ID, 0, 20))
                .isInstanceOfSatisfying(ProjectException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND));
        verifyNoInteractions(memberRepository, boardRepository);
    }

    @Test
    void 생성자_ID가_없으면_프로젝트를_조회하지_않는다() {
        // given
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        // when / then
        assertThatThrownBy(() -> service.create(null, PROJECT_CODE, USER_ID, new BoardCreateRequest("보드")))
                .isInstanceOf(ProjectException.class);
        assertThatThrownBy(() -> service.getAll(null, PROJECT_CODE, USER_ID, 0, 20))
                .isInstanceOf(ProjectException.class);
        verifyNoInteractions(projectRepository, memberRepository, boardRepository);
    }

    @Test
    void 빈_수정_요청은_조회_없이_거절한다() {
        // given / when / then
        assertThatThrownBy(() -> service.update(CREATOR_ID, PROJECT_CODE, USER_ID, null))
                .isInstanceOfSatisfying(BoardException.class, e -> assertThat(e.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_UPDATE));
        assertThatThrownBy(() -> service.update(CREATOR_ID, PROJECT_CODE, USER_ID, new BoardUpdateRequest(null)))
                .isInstanceOfSatisfying(BoardException.class, e -> assertThat(e.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_UPDATE));
        assertThatThrownBy(() -> service.update(CREATOR_ID, PROJECT_CODE, USER_ID, new BoardUpdateRequest(List.of())))
                .isInstanceOfSatisfying(BoardException.class, e -> assertThat(e.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_UPDATE));
        verifyNoInteractions(projectRepository, userRepository, memberRepository, boardRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void 공백_이름_수정은_조회_없이_거절한다(String name) {
        // given / when / then
        var request = new BoardUpdateRequest(List.of(new BoardUpdateItem(1L, name)));
        assertThatThrownBy(() -> service.update(CREATOR_ID, PROJECT_CODE, USER_ID, request))
                .isInstanceOfSatisfying(BoardException.class, e -> assertThat(e.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_NAME));
        verifyNoInteractions(projectRepository, userRepository, memberRepository, boardRepository);
    }

    @Test
    void 중복된_보드_ID는_조회_없이_거절한다() {
        // given
        var request = new BoardUpdateRequest(List.of(
                new BoardUpdateItem(1L, null),
                new BoardUpdateItem(1L, "변경")));

        // when / then
        assertThatThrownBy(() -> service.update(CREATOR_ID, PROJECT_CODE, USER_ID, request))
                .isInstanceOfSatisfying(BoardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_UPDATE));
        verifyNoInteractions(projectRepository, userRepository, memberRepository, boardRepository);
    }

    @Test
    void null인_보드_항목은_조회_없이_거절한다() {
        // given
        var request = new BoardUpdateRequest(Collections.singletonList(null));

        // when / then
        assertThatThrownBy(() -> service.update(CREATOR_ID, PROJECT_CODE, USER_ID, request))
                .isInstanceOfSatisfying(BoardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_UPDATE));
        verifyNoInteractions(projectRepository, userRepository, memberRepository, boardRepository);
    }

    @Test
    void null인_보드_ID는_조회_없이_거절한다() {
        // given
        var request = new BoardUpdateRequest(List.of(new BoardUpdateItem(null, null)));

        // when / then
        assertThatThrownBy(() -> service.update(CREATOR_ID, PROJECT_CODE, USER_ID, request))
                .isInstanceOfSatisfying(BoardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_INVALID_UPDATE));
        verifyNoInteractions(projectRepository, userRepository, memberRepository, boardRepository);
    }

    @Test
    void 전체_보드의_순서와_이름을_한번에_수정한다() {
        // given
        Project project = spy(Project.create(
                User.create("생성자", "update@example.com", null), PROJECT_CODE, "프로젝트", null));
        Board first = mock(Board.class);
        Board second = mock(Board.class);
        when(first.getProject()).thenReturn(project);
        when(first.getId()).thenReturn(1L);
        when(first.getName()).thenReturn("첫 보드");
        when(second.getProject()).thenReturn(project);
        when(second.getId()).thenReturn(2L);
        when(second.getName()).thenReturn("둘째 보드");
        project.registerBoard(first);
        project.registerBoard(second);
        doReturn(PROJECT_ID).when(project).getId();
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(projectRepository.findByCreatorIdAndCodeForUpdate(CREATOR_ID, PROJECT_CODE))
                .thenReturn(Optional.of(project));
        when(memberRepository.existsByUserIdAndProjectId(USER_ID, PROJECT_ID)).thenReturn(true);
        var request = new BoardUpdateRequest(List.of(
                new BoardUpdateItem(2L, "변경"),
                new BoardUpdateItem(1L, null)));

        // when
        var responses = service.update(CREATOR_ID, PROJECT_CODE, USER_ID, request);

        // then
        assertThat(project.getBoards()).containsExactly(second, first);
        assertThat(responses).extracting(response -> response.id()).containsExactly(2L, 1L);
        verify(second).rename("변경");
        verify(first, never()).rename(anyString());
        verify(second).synchronizeSortOrder(0);
        verify(first).synchronizeSortOrder(1);
        verify(boardRepository).flush();
    }
}
