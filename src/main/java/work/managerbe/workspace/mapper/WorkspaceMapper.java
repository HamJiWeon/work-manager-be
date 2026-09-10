package work.managerbe.workspace.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import work.managerbe.workspace.domain.Workspace;
import work.managerbe.workspace.dto.response.WorkspaceResponse;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

    @Mapping(source = "project.id", target = "projectId")
    WorkspaceResponse toResponse(Workspace workspace);
}
