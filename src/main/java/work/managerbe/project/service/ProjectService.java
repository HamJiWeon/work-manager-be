package work.managerbe.project.service;

import work.managerbe.project.dto.request.ProjectCreateRequest;
import work.managerbe.project.dto.request.ProjectUpdateRequest;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.dto.response.ProjectSliceResponse;

import java.util.UUID;

public interface ProjectService {

    ProjectResponse create(UUID userId, ProjectCreateRequest request);

    ProjectResponse get(UUID creatorId, String code, UUID requesterId);

    ProjectSliceResponse getAll(UUID pathUserId, UUID requesterId, int page);

    ProjectResponse update(UUID creatorId, String code, UUID requesterId, ProjectUpdateRequest request);

    void delete(UUID creatorId, String code, UUID requesterId);
}
