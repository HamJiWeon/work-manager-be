package work.managerbe.workspace.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Slice;
import work.managerbe.workspace.domain.Workspace;
import work.managerbe.workspace.dto.response.WorkspaceResponse;
import work.managerbe.workspace.dto.response.WorkspaceSliceResponse;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

    @Mapping(source = "project.id", target = "projectId")
    WorkspaceResponse toResponse(Workspace workspace);

    @Mapping(target = "page", source = "number")
    @Mapping(target = "hasPrevious", expression = "java(workspaces.hasPrevious())")
    @Mapping(target = "hasNext", expression = "java(workspaces.hasNext())")
    WorkspaceSliceResponse toSliceResponse(Slice<WorkspaceResponse> workspaces);
}
