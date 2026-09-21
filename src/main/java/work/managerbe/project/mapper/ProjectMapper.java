package work.managerbe.project.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Slice;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.dto.response.ProjectSliceResponse;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);

    /**
     * 프로젝트 응답 슬라이스의 페이지 위치와 이전·다음 페이지 존재 여부를 응답 DTO로 변환한다.
     */
    @Mapping(target = "page", source = "number")
    @Mapping(target = "hasPrevious", expression = "java(projects.hasPrevious())")
    @Mapping(target = "hasNext", expression = "java(projects.hasNext())")
    ProjectSliceResponse toSliceResponse(Slice<ProjectResponse> projects);
}
