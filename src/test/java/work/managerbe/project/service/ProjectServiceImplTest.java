package work.managerbe.project.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.managerbe.global.project.ProjectErrorCode;
import work.managerbe.global.project.ProjectException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.request.ProjectCreateRequest;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.mapper.ProjectMapper;
import work.managerbe.project.repository.ProjectRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
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

    @Test
    @DisplayName("카드 접두사에 UUID를 붙여 프로젝트를 저장한다.")
    void 프로젝트_생성() {
        // given
        UUID userId = UUID.randomUUID();
        ProjectCreateRequest request =
                new ProjectCreateRequest("업무 관리", "WORK", "프로젝트 설명");

        Project savedProject = mock(Project.class);
        ProjectResponse expectedResponse = mock(ProjectResponse.class);

        when(projectRepository.save(any(Project.class)))
                .thenReturn(savedProject);
        when(mapper.toResponse(savedProject))
                .thenReturn(expectedResponse);
        // when
        ProjectResponse response = projectService.create(userId, request);

        // then
        ArgumentCaptor<Project> captor =
                ArgumentCaptor.forClass(Project.class);

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
    @DisplayName("프로젝트 이름이 없으면 예외가 발생한다.")
    void 이름_누락() {
        // given
        UUID userId = UUID.randomUUID();
        ProjectCreateRequest request =
                new ProjectCreateRequest(null, "WORK", "프로젝트 설명");

        // when
        ProjectException exception = catchThrowableOfType(
                () -> projectService.create(userId, request),
                ProjectException.class
        );

        // then
        assertThat(exception).isNotNull();
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
        ProjectException exception = catchThrowableOfType(
                () -> projectService.create(userId, request),
                ProjectException.class
        );

        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode())
                .isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE_NAME);

        verifyNoInteractions(projectRepository, mapper);
    }
}