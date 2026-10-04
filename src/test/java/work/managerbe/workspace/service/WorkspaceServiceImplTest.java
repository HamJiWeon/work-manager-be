package work.managerbe.workspace.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.managerbe.global.exception.CommonException;
import work.managerbe.global.exception.ErrorCode;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.global.exception.workspace.WorkspaceErrorCode;
import work.managerbe.global.exception.workspace.WorkspaceException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.repository.UserRepository;
import work.managerbe.workspace.domain.Workspace;
import work.managerbe.workspace.dto.request.WorkspaceCreateRequest;
import work.managerbe.workspace.dto.response.WorkspaceResponse;
import work.managerbe.workspace.mapper.WorkspaceMapper;
import work.managerbe.workspace.repository.WorkspaceRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 생성 요청의 사용자와 프로젝트를 검증하고 워크스페이스를 저장한 뒤 응답으로 변환하는 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceServiceImplTest {

    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID REQUESTER_ID = CREATOR_ID;
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final String PROJECT_CODE = "WORK";
    private static final Long WORKSPACE_ID = 5L;

    @Mock
    WorkspaceRepository workspaceRepository;

    @Mock
    ProjectRepository projectRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    WorkspaceMapper mapper;

    @InjectMocks
    WorkspaceServiceImpl service;

    @Test
    void 정상_요청이면_워크스페이스를_저장하고_응답을_반환한다() {
        // given
        Project project = mock(Project.class);
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("프로젝트 개발 가이드", "# 개발 가이드");
        WorkspaceResponse expected = new WorkspaceResponse(
                5L, 1L, request.title(), request.content(), LocalDateTime.now(), LocalDateTime.now());
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(projectRepository.findByCreatorIdAndCodeForUpdate(CREATOR_ID, PROJECT_CODE))
                .thenReturn(Optional.of(project));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Workspace.class))).thenReturn(expected);

        // when
        WorkspaceResponse response = service.create(CREATOR_ID, PROJECT_CODE, REQUESTER_ID, request);

        // then
        ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(captor.capture());
        Workspace savedWorkspace = captor.getValue();
        assertThat(savedWorkspace.getProject()).isSameAs(project);
        assertThat(savedWorkspace.getTitle()).isEqualTo(request.title());
        assertThat(savedWorkspace.getContent()).isEqualTo(request.content());
        verify(mapper).toResponse(savedWorkspace);
        assertThat(response).isSameAs(expected);
    }

    @Test
    void 요청이_null이면_저장하지_않는다() {
        // when / then
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, REQUESTER_ID, null))
                .isInstanceOfSatisfying(CommonException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(userRepository, projectRepository, workspaceRepository, mapper);
    }

    @Test
    void 생성자가_존재하지_않으면_저장하지_않는다() {
        // given
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("제목", "내용");
        when(userRepository.existsById(CREATOR_ID)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, REQUESTER_ID, request))
                .isInstanceOfSatisfying(UserException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(projectRepository, workspaceRepository, mapper);
    }

    @Test
    void 요청자가_프로젝트_생성자가_아니면_저장하지_않는다() {
        // given
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("제목", "내용");
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(userRepository.existsById(OTHER_USER_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, OTHER_USER_ID, request))
                .isInstanceOfSatisfying(CommonException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(projectRepository, workspaceRepository, mapper);
    }

    @Test
    void 프로젝트_코드가_공백이면_저장하지_않는다() {
        // given
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("제목", "내용");
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.create(CREATOR_ID, " ", REQUESTER_ID, request))
                .isInstanceOfSatisfying(ProjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE));
        verifyNoInteractions(projectRepository, workspaceRepository, mapper);
    }

    @Test
    void 프로젝트가_존재하지_않으면_저장하지_않는다() {
        // given
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("제목", "내용");
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(projectRepository.findByCreatorIdAndCodeForUpdate(CREATOR_ID, PROJECT_CODE))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.create(CREATOR_ID, PROJECT_CODE, REQUESTER_ID, request))
                .isInstanceOfSatisfying(ProjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND));
        verifyNoInteractions(workspaceRepository, mapper);
    }

    @Test
    void 단건_조회에_성공하면_프로젝트_경로로_조회하고_응답을_반환한다() {
        // given
        Workspace workspace = mock(Workspace.class);
        WorkspaceResponse expected = new WorkspaceResponse(
                WORKSPACE_ID, 1L, "프로젝트 개발 가이드", "# 개발 가이드",
                LocalDateTime.now(), LocalDateTime.now());
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(workspaceRepository.findByProjectPath(WORKSPACE_ID, CREATOR_ID, PROJECT_CODE))
                .thenReturn(Optional.of(workspace));
        when(mapper.toResponse(workspace)).thenReturn(expected);

        // when
        WorkspaceResponse response = service.get(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, REQUESTER_ID);

        // then
        verify(workspaceRepository).findByProjectPath(WORKSPACE_ID, CREATOR_ID, PROJECT_CODE);
        verify(mapper).toResponse(workspace);
        assertThat(response).isSameAs(expected);
    }

    @Test
    void 프로젝트_경로에_속한_워크스페이스가_없으면_조회하지_못한다() {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(workspaceRepository.findByProjectPath(WORKSPACE_ID, CREATOR_ID, PROJECT_CODE))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.get(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, REQUESTER_ID))
                .isInstanceOfSatisfying(WorkspaceException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        verify(workspaceRepository).findByProjectPath(WORKSPACE_ID, CREATOR_ID, PROJECT_CODE);
        verifyNoInteractions(mapper);
    }

    @Test
    void 단건_조회_요청자가_프로젝트_생성자가_아니면_조회하지_못한다() {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(userRepository.existsById(OTHER_USER_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.get(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, OTHER_USER_ID))
                .isInstanceOfSatisfying(CommonException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(workspaceRepository, mapper);
    }
}
