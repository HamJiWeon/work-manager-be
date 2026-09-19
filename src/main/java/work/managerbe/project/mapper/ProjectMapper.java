package work.managerbe.project.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.response.ProjectPageResponse;
import work.managerbe.project.dto.response.ProjectResponse;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);

    @Mapping(target = "page", source = "number")
    ProjectPageResponse toPageResponse(Page<ProjectResponse> projects);
}
