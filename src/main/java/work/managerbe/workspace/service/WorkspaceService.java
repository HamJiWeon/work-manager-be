package work.managerbe.workspace.service;

import work.managerbe.workspace.dto.request.WorkspaceCreateRequest;
import work.managerbe.workspace.dto.response.WorkspaceResponse;

import java.util.UUID;

public interface WorkspaceService {

    WorkspaceResponse create(UUID userId, String code, UUID requesterId, WorkspaceCreateRequest request);

    WorkspaceResponse get(UUID userId, String code, Long workspaceId, UUID requesterId);
}
