package work.managerbe.workspace.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.managerbe.project.domain.Project;
import work.managerbe.workspace.domain.Workspace;
import work.managerbe.workspace.dto.response.WorkspaceResponse;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 실제 MapStruct 구현체로 응답 필드 변환과 null 입력 처리를 검증한다.
 * 영속성 필드는 Mockito로 반환값을 지정한다.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceMapperTest {

    @Mock
    Workspace workspace;

    @Mock
    Project project;

    private final WorkspaceMapper mapper = new WorkspaceMapperImpl();

    @Nested
    @DisplayName("워크스페이스 응답 변환")
    class ToResponse {

        @Test
        @DisplayName("워크스페이스의 모든 필드를 응답으로 변환한다.")
        void 전체_필드_변환() {
            // given
            LocalDateTime createdAt = LocalDateTime.parse("2026-09-15T10:00:00");
            LocalDateTime updatedAt = LocalDateTime.parse("2026-09-15T11:00:00");

            when(workspace.getId()).thenReturn(1L);
            when(workspace.getProject()).thenReturn(project);
            when(project.getId()).thenReturn(2L);
            when(workspace.getTitle()).thenReturn("회의록");
            when(workspace.getContent()).thenReturn("회의 내용");
            when(workspace.getCreatedAt()).thenReturn(createdAt);
            when(workspace.getUpdatedAt()).thenReturn(updatedAt);

            // when
            WorkspaceResponse response = mapper.toResponse(workspace);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.projectId()).isEqualTo(2L);
            assertThat(response.title()).isEqualTo("회의록");
            assertThat(response.content()).isEqualTo("회의 내용");
            assertThat(response.createdAt()).isEqualTo(createdAt);
            assertThat(response.updatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("저장 전 프로젝트의 ID는 null로 변환한다.")
        void 프로젝트_ID가_없으면_null_반환() {
            // given
            Project newProject = Project.create("WORK_test", "업무 관리", "설명");
            Workspace newWorkspace = Workspace.create(newProject, "회의록", "회의 내용");

            // when
            WorkspaceResponse response = mapper.toResponse(newWorkspace);

            // then
            assertThat(response.projectId()).isNull();
            assertThat(response.title()).isEqualTo("회의록");
            assertThat(response.content()).isEqualTo("회의 내용");
        }

        @Test
        @DisplayName("프로젝트가 없으면 projectId는 null로 변환한다.")
        void 프로젝트가_없으면_projectId_null_반환() {
            // given
            Workspace newWorkspace = Workspace.create(null, "회의록", "회의 내용");

            // when
            WorkspaceResponse response = mapper.toResponse(newWorkspace);

            // then
            assertThat(response.projectId()).isNull();
            assertThat(response.title()).isEqualTo("회의록");
            assertThat(response.content()).isEqualTo("회의 내용");
        }

        @Test
        @DisplayName("워크스페이스가 null이면 null을 반환한다.")
        void 워크스페이스가_null이면_null_반환() {
            // given
            Workspace nullWorkspace = null;

            // when
            WorkspaceResponse response = mapper.toResponse(nullWorkspace);

            // then
            assertThat(response).isNull();
        }
    }
}