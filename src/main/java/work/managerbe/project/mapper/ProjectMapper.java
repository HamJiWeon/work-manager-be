package work.managerbe.project.mapper;

import org.mapstruct.Mapper;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.response.ProjectResponse;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);
}
