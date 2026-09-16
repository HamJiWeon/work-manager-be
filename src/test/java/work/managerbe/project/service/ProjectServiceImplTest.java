package work.managerbe.project.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.request.ProjectCreateRequest;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.mapper.ProjectMapper;
import work.managerbe.project.repository.ProjectRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMapper mapper;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("중복이 없으면 카드 접두사를 대문자로 변환하고 UUID를 붙여 저장한다.")
        void 프로젝트_생성() {
            // given
            String prefix = "work";
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest("업무 관리", prefix, "프로젝트 설명");

            Project savedProject = mock(Project.class);
            ProjectResponse expectedResponse = mock(ProjectResponse.class);

            when(projectRepository.findByUser_Id(userId)).thenReturn(List.of());
            when(projectRepository.save(any(Project.class)))
                    .thenReturn(savedProject);
            when(mapper.toResponse(savedProject))
                    .thenReturn(expectedResponse);
            // when
            ProjectResponse response = projectService.create(userId, request);

            // then
            ArgumentCaptor<Project> captor =
                    ArgumentCaptor.forClass(Project.class);

            verify(projectRepository).findByUser_Id(userId);
            verify(projectRepository).save(captor.capture());

            Project project = captor.getValue();

            assertThat(project.getCode()).matches(
                    "^WORK_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
            );
            assertThat(project.getName()).isEqualTo(request.name());
            assertThat(project.getDescription()).isEqualTo(request.description());
            assertThat(response).isSameAs(expectedResponse);

            verify(mapper).toResponse(savedProject);
        }

        @Test
        @DisplayName("사용자의 기존 접두사와 대소문자 구분 없이 중복되면 저장하지 않는다.")
        void 사용자_접두사_중복() {
            // given
            String prefix = "work";
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request = new ProjectCreateRequest("업무 관리", prefix, null);
            Project other = Project.create("TASK_" + UUID.randomUUID(), "다른 프로젝트", null);
            Project existing = Project.create("WORK_" + UUID.randomUUID(), "기존 프로젝트", null);
            when(projectRepository.findByUser_Id(userId)).thenReturn(List.of(other, existing));

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_DUPLICATE_PREFIX);
            verify(projectRepository).findByUser_Id(userId);
            verify(projectRepository, never()).save(any(Project.class));
            verifyNoInteractions(mapper);
        }

        @Test
        @DisplayName("사용자의 기존 접두사와 일부만 일치하면 생성할 수 있다.")
        void 다른_접두사로_프로젝트_생성() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request = new ProjectCreateRequest("업무 관리", "WORK", null);
            Project existing = Project.create("WORKFLOW_" + UUID.randomUUID(), "기존 프로젝트", null);
            Project savedProject = mock(Project.class);
            ProjectResponse expectedResponse = mock(ProjectResponse.class);
            when(projectRepository.findByUser_Id(userId)).thenReturn(List.of(existing));
            when(projectRepository.save(any(Project.class))).thenReturn(savedProject);
            when(mapper.toResponse(savedProject)).thenReturn(expectedResponse);

            // when
            ProjectResponse response = projectService.create(userId, request);

            // then
            assertThat(response).isSameAs(expectedResponse);
            verify(projectRepository).findByUser_Id(userId);
            verify(projectRepository).save(any(Project.class));
            verify(mapper).toResponse(savedProject);
        }

        @Test
        @DisplayName("카드 접두사에 밑줄이 포함되면 예외가 발생한다.")
        void 카드접두사_밑줄_포함() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest("업무 관리", "WORK_TASK", "프로젝트 설명");

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_INVALID_PREFIX);
            assertThat(exception.getErrorCode().getHttpStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("프로젝트 이름이 없으면 예외가 발생한다.")
        void 이름_누락() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest(null, "WORK", "프로젝트 설명");

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE_NAME);

            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("카드 접두사가 공백이면 예외가 발생한다.")
        void 카드접두사_공백() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest("업무 관리", "   ", "프로젝트 설명");

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE_NAME);

            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("카드 접두사가 null이면 예외가 발생한다.")
        void 카드접두사_누락() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest("업무 관리", null, "프로젝트 설명");

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE_NAME);

            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("프로젝트 이름이 공백이면 예외가 발생한다.")
        void 이름_공백() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest("   ", "WORK", "프로젝트 설명");

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE_NAME);

            verifyNoInteractions(projectRepository, mapper);
        }
    }

    @Nested
    @DisplayName("get")
    class Get {
        @Test
        @DisplayName("사용자의 프로젝트 중 접두사가 정확히 일치하는 프로젝트를 반환한다.")
        void 프로젝트_단건_조회() {
            // given
            UUID userId = UUID.randomUUID();
            Project other = Project.create("WORKFLOW_550e8400-e29b-41d4-a716-446655440000", "다른 프로젝트", null);
            Project project = Project.create("WORK_123e4567-e89b-12d3-a456-426614174000", "업무 관리", null);
            ProjectResponse expectedResponse = mock(ProjectResponse.class);
            when(projectRepository.findByUser_Id(userId)).thenReturn(List.of(other, project));
            when(mapper.toResponse(project)).thenReturn(expectedResponse);

            // when
            ProjectResponse response = projectService.get(userId, "WORK");

            // then
            assertThat(response).isSameAs(expectedResponse);
            verify(projectRepository).findByUser_Id(userId);
            verify(mapper).toResponse(project);
            verify(mapper, never()).toResponse(other);
        }

        @Test
        @DisplayName("일치하는 접두사가 없으면 프로젝트를 찾을 수 없다는 예외가 발생한다.")
        void 조회할_접두사가_없음() {
            // given
            UUID userId = UUID.randomUUID();
            Project project = Project.create("WORKFLOW_550e8400-e29b-41d4-a716-446655440000", "다른 프로젝트", null);
            when(projectRepository.findByUser_Id(userId)).thenReturn(List.of(project));

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.get(userId, "WORK")
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND);
            verifyNoInteractions(mapper);
        }

        @Test
        @DisplayName("사용자의 프로젝트가 없으면 프로젝트를 찾을 수 없다는 예외가 발생한다.")
        void 조회할_프로젝트가_없음() {
            // given
            UUID userId = UUID.randomUUID();
            when(projectRepository.findByUser_Id(userId)).thenReturn(List.of());

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.get(userId, "WORK")
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND);
            verifyNoInteractions(mapper);
        }

        @Test
        @DisplayName("조회할 접두사가 null이면 예외가 발생한다.")
        void 조회_접두사_누락() {
            // given
            UUID userId = UUID.randomUUID();

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.get(userId, null)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE_NAME);
            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("조회할 접두사가 공백이면 예외가 발생한다.")
        void 조회_접두사_공백() {
            // given
            UUID userId = UUID.randomUUID();

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.get(userId, "   ")
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE_NAME);
            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("조회할 사용자 ID가 null이면 예외가 발생한다.")
        void 조회_사용자_누락() {
            // given
            String code = "WORK";

            // when
            UserException exception = assertThrows(
                    UserException.class,
                    () -> projectService.get(null, code)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
            verifyNoInteractions(projectRepository, mapper);
        }

    }

}