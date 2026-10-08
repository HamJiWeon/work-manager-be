package work.managerbe.workspace.service;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import work.managerbe.workspace.dto.response.WorkspaceSliceResponse;
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
import work.managerbe.workspace.dto.request.WorkspaceUpdateRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

    /** 저장소의 Slice를 DTO로 변환할 때 페이지 정보가 유지되는지 검증한다. */
    @Test
    void 목록_조회는_열_개_단위로_요청하고_변환한_Slice를_전달한다() {
        // given
        Workspace workspace = mock(Workspace.class);
        WorkspaceResponse item = new WorkspaceResponse(5L, 1L, "제목", "내용", null, null);
        PageRequest pageable = PageRequest.of(1, 10);
        WorkspaceSliceResponse expected = new WorkspaceSliceResponse(List.of(item), 1, 10, true, true);
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(workspaceRepository.findAllByProjectPath(CREATOR_ID, PROJECT_CODE, pageable))
                .thenReturn(new SliceImpl<>(List.of(workspace), pageable, true));
        when(mapper.toResponse(workspace)).thenReturn(item);
        when(mapper.toSliceResponse(any())).thenReturn(expected);

        // when
        WorkspaceSliceResponse result = service.getAll(CREATOR_ID, PROJECT_CODE, REQUESTER_ID, 1);

        // then
        ArgumentCaptor<Slice<WorkspaceResponse>> captor = ArgumentCaptor.captor();
        verify(mapper).toSliceResponse(captor.capture());
        assertThat(captor.getValue().getContent()).containsExactly(item);
        assertThat(captor.getValue().getNumber()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(10);
        assertThat(captor.getValue().hasPrevious()).isTrue();
        assertThat(captor.getValue().hasNext()).isTrue();
        assertThat(result).isSameAs(expected);
    }

    @Test
    void 목록_조회_페이지가_음수이면_저장소를_조회하지_않는다() {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.getAll(CREATOR_ID, PROJECT_CODE, REQUESTER_ID, -1))
                .isInstanceOfSatisfying(CommonException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(workspaceRepository, mapper);
    }

    @Test
    void 목록_조회_요청자가_생성자가_아니면_저장소를_조회하지_않는다() {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(userRepository.existsById(OTHER_USER_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.getAll(CREATOR_ID, PROJECT_CODE, OTHER_USER_ID, 0))
                .isInstanceOfSatisfying(CommonException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(workspaceRepository, mapper);
    }


    /** 잠금 조회로 얻은 엔티티의 부분 수정 결과와 flush 후 응답 변환 순서를 검증한다. */
    @ParameterizedTest
    @CsvSource(value = {
            "새 제목,새 내용,새 제목,새 내용",
            "새 제목,NULL,새 제목,기존 내용",
            "NULL,새 내용,기존 제목,새 내용",
            "NULL,NULL,기존 제목,기존 내용",
            "'','', '', ''"
    }, nullValues = "NULL")
    void 수정은_전달한_필드만_반영하고_응답을_반환한다(
            String title, String content, String expectedTitle, String expectedContent) {
        // given
        Workspace workspace = Workspace.create(null, "기존 제목", "기존 내용");
        WorkspaceUpdateRequest request = new WorkspaceUpdateRequest(title, content);
        WorkspaceResponse expected = new WorkspaceResponse(
                WORKSPACE_ID, 1L, expectedTitle, expectedContent, null, null);
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(workspaceRepository.findByProjectPathForUpdate(WORKSPACE_ID, CREATOR_ID, PROJECT_CODE))
                .thenReturn(Optional.of(workspace));
        when(mapper.toResponse(workspace)).thenReturn(expected);

        // when
        WorkspaceResponse result = service.update(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, REQUESTER_ID, request);

        // then
        assertThat(workspace.getTitle()).isEqualTo(expectedTitle);
        assertThat(workspace.getContent()).isEqualTo(expectedContent);
        assertThat(result).isSameAs(expected);
        var order = inOrder(workspaceRepository, mapper);
        order.verify(workspaceRepository).findByProjectPathForUpdate(WORKSPACE_ID, CREATOR_ID, PROJECT_CODE);
        order.verify(workspaceRepository).flush();
        order.verify(mapper).toResponse(workspace);
    }

    /** 요청 객체 자체가 null이면 필드가 모두 null인 요청과 달리 거부하는지 검증한다. */
    @Test
    void 수정_요청_객체가_null이면_거부한다() {
        // given
        WorkspaceUpdateRequest request = null;

        // when / then
        assertThatThrownBy(() -> service.update(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, REQUESTER_ID, request))
                .isInstanceOfSatisfying(CommonException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verifyNoInteractions(userRepository, workspaceRepository, mapper);
    }

    /** 다른 사용자는 워크스페이스 조회 전에 수정 권한 검사에서 차단되는지 검증한다. */
    @Test
    void 수정_요청자가_생성자가_아니면_거부한다() {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(userRepository.existsById(OTHER_USER_ID)).thenReturn(true);
        WorkspaceUpdateRequest request = new WorkspaceUpdateRequest("새 제목", null);

        // when / then
        assertThatThrownBy(() -> service.update(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, OTHER_USER_ID, request))
                .isInstanceOfSatisfying(CommonException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(workspaceRepository, mapper);
    }

    /** 경로에 속한 워크스페이스가 없으면 flush와 응답 변환 없이 실패하는지 검증한다. */
    @Test
    void 수정할_워크스페이스가_프로젝트_경로에_없으면_거부한다() {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(workspaceRepository.findByProjectPathForUpdate(WORKSPACE_ID, CREATOR_ID, PROJECT_CODE))
                .thenReturn(Optional.empty());
        WorkspaceUpdateRequest request = new WorkspaceUpdateRequest("새 제목", null);

        // when / then
        assertThatThrownBy(() -> service.update(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, REQUESTER_ID, request))
                .isInstanceOfSatisfying(WorkspaceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        verify(workspaceRepository, never()).flush();
        verifyNoInteractions(mapper);
    }

    /** null과 빈 문자열 및 공백인 프로젝트 코드를 모두 거부하는지 검증한다. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void 수정_프로젝트_코드가_유효하지_않으면_거부한다(String code) {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        WorkspaceUpdateRequest request = new WorkspaceUpdateRequest("새 제목", null);

        // when / then
        assertThatThrownBy(() -> service.update(CREATOR_ID, code, WORKSPACE_ID, REQUESTER_ID, request))
                .isInstanceOfSatisfying(ProjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE));
        verifyNoInteractions(workspaceRepository, mapper);
    }

    /** 존재하지 않는 생성자 또는 요청자가 저장소 수정에 도달하지 않는지 검증한다. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 수정_사용자가_존재하지_않으면_거부한다(boolean missingCreator) {
        // given
        UUID creatorId = CREATOR_ID;
        UUID requesterId = OTHER_USER_ID;
        if (!missingCreator) {
            when(userRepository.existsById(creatorId)).thenReturn(true);
        }
        WorkspaceUpdateRequest request = new WorkspaceUpdateRequest("새 제목", null);

        // when / then
        assertThatThrownBy(() -> service.update(creatorId, PROJECT_CODE, WORKSPACE_ID, requesterId, request))
                .isInstanceOfSatisfying(UserException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(workspaceRepository, mapper);
    }

    /** 생성자의 삭제 요청은 경로로 잠금 조회한 대상만 삭제한다. */
    @Test
    void 삭제는_프로젝트_경로로_잠금_조회한_워크스페이스를_삭제한다() {
        // given
        Workspace workspace = Workspace.create(null, "제목", "내용");
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(workspaceRepository.findByProjectPathForUpdate(WORKSPACE_ID, CREATOR_ID, PROJECT_CODE))
                .thenReturn(Optional.of(workspace));

        // when
        service.delete(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, REQUESTER_ID);

        // then
        verify(workspaceRepository).delete(workspace);
        verifyNoInteractions(projectRepository, mapper);
    }

    /** 비소유자의 삭제 요청이 데이터 조회 전에 차단되는지 검증한다. */
    @Test
    void 삭제_요청자가_생성자가_아니면_거부한다() {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(userRepository.existsById(OTHER_USER_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.delete(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, OTHER_USER_ID))
                .isInstanceOfSatisfying(CommonException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(workspaceRepository, projectRepository, mapper);
    }

    /** 경로에 대상이 없으면 삭제를 실행하지 않는지 검증한다. */
    @Test
    void 삭제_대상이_프로젝트_경로에_없으면_거부한다() {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(workspaceRepository.findByProjectPathForUpdate(WORKSPACE_ID, CREATOR_ID, PROJECT_CODE))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.delete(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, REQUESTER_ID))
                .isInstanceOfSatisfying(WorkspaceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        verify(workspaceRepository, never()).delete(any(Workspace.class));
    }

    /** null과 빈 문자열 및 공백 코드는 삭제 조회 전에 거부한다. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void 삭제_프로젝트_코드가_유효하지_않으면_거부한다(String code) {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.delete(CREATOR_ID, code, WORKSPACE_ID, REQUESTER_ID))
                .isInstanceOfSatisfying(ProjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE));
        verifyNoInteractions(workspaceRepository);
    }

    /** 존재하지 않는 생성자와 요청자의 삭제 요청을 각각 거부한다. */
    @Test
    void 삭제_생성자가_존재하지_않으면_거부한다() {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.delete(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, REQUESTER_ID))
                .isInstanceOfSatisfying(UserException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(workspaceRepository);
    }

    /** 생성자가 존재해도 요청자가 없으면 삭제를 실행하지 않는다. */
    @Test
    void 삭제_요청자가_존재하지_않으면_거부한다() {
        // given
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(userRepository.existsById(OTHER_USER_ID)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.delete(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, OTHER_USER_ID))
                .isInstanceOfSatisfying(UserException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(workspaceRepository);
    }

}
