package work.managerbe.project.service;

import work.managerbe.project.dto.request.ProjectCreateRequest;
import work.managerbe.project.dto.response.ProjectResponse;

import java.util.UUID;

public interface ProjectService {

    ProjectResponse create(UUID userId, ProjectCreateRequest request);
}
