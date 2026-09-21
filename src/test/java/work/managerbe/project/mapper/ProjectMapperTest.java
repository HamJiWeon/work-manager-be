package work.managerbe.project.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.dto.response.ProjectSliceResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 실제 MapStruct 구현체로 프로젝트와 프로젝트 슬라이스 응답의 전체 필드를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectMapperTest {

    @Mock
    Project project;

    private final ProjectMapper mapper = new ProjectMapperImpl();

    @Nested
    @DisplayName("프로젝트 응답 변환")
    class ToResponse {

        @Test
        @DisplayName("프로젝트의 모든 필드를 응답으로 변환한다.")
        void 전체_필드_변환() {
            // given
            LocalDateTime createdAt = LocalDateTime.parse("2026-09-19T10:00:00");
            LocalDateTime updatedAt = LocalDateTime.parse("2026-09-19T11:00:00");

            when(project.getId()).thenReturn(1L);
            when(project.getCode()).thenReturn("WORK");
            when(project.getName()).thenReturn("업무 관리");
            when(project.getNextCardNumber()).thenReturn(3L);
            when(project.getDescription()).thenReturn("프로젝트 설명");
            when(project.getCreatedAt()).thenReturn(createdAt);
            when(project.getUpdatedAt()).thenReturn(updatedAt);

            // when
            ProjectResponse response = mapper.toResponse(project);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.code()).isEqualTo("WORK");
            assertThat(response.name()).isEqualTo("업무 관리");
            assertThat(response.nextCardNumber()).isEqualTo(3L);
            assertThat(response.description()).isEqualTo("프로젝트 설명");
            assertThat(response.createdAt()).isEqualTo(createdAt);
            assertThat(response.updatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("프로젝트가 null이면 null을 반환한다.")
        void 프로젝트가_null이면_null_반환() {
            // when
            ProjectResponse response = mapper.toResponse(null);

            // then
            assertThat(response).isNull();
        }
    }

    @Nested
    @DisplayName("프로젝트 슬라이스 응답 변환")
    class ToSliceResponse {

        @Test
        @DisplayName("목록과 슬라이스 정보를 응답으로 변환한다.")
        void 전체_슬라이스_정보_변환() {
            // given
            ProjectResponse first = new ProjectResponse(
                    1L, "FIRST", "첫 프로젝트", 1L, null, null, null);
            ProjectResponse second = new ProjectResponse(
                    2L, "SECOND", "둘째 프로젝트", 1L, null, null, null);
            Slice<ProjectResponse> projects = new SliceImpl<>(
                    List.of(first, second),
                    PageRequest.of(1, 10),
                    true
            );

            // when
            ProjectSliceResponse response = mapper.toSliceResponse(projects);

            // then
            assertThat(response.content()).containsExactly(first, second);
            assertThat(response.page()).isEqualTo(1);
            assertThat(response.size()).isEqualTo(10);
            assertThat(response.hasPrevious()).isTrue();
            assertThat(response.hasNext()).isTrue();
        }

        @Test
        @DisplayName("프로젝트 슬라이스가 null이면 null을 반환한다.")
        void 프로젝트_슬라이스가_null이면_null_반환() {
            // when
            ProjectSliceResponse response = mapper.toSliceResponse(null);

            // then
            assertThat(response).isNull();
        }
    }
}
