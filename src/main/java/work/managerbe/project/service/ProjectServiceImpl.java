package work.managerbe.project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.project.ProjectErrorCode;
import work.managerbe.global.project.ProjectException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.request.ProjectCreateRequest;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.mapper.ProjectMapper;
import work.managerbe.project.repository.ProjectRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService{

    private final ProjectRepository projectRepository;
    private final ProjectMapper mapper;

    @Override
    @Transactional
    public ProjectResponse create(UUID userId ,ProjectCreateRequest request) {
        if(request.cardPrefix() == null || request.name() == null || request.cardPrefix().isBlank() || request.name().isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_INVALID_CODE_NAME);
        }

        // memberService에 사용자 할당 로직 추후 추가

        String code = request.cardPrefix() + "_" + UUID.randomUUID();
        Project project = Project.create(code, request.name(), request.description());
        return mapper.toResponse(projectRepository.save(project));
    }
}
