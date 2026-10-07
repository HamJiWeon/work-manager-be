package work.managerbe.workspace.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.exception.CommonException;
import work.managerbe.global.exception.ErrorCode;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.global.exception.workspace.WorkspaceErrorCode;
import work.managerbe.global.exception.workspace.WorkspaceException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.repository.UserRepository;
import work.managerbe.workspace.domain.Workspace;
import work.managerbe.workspace.dto.request.WorkspaceCreateRequest;
import work.managerbe.workspace.dto.response.WorkspaceResponse;
import work.managerbe.workspace.dto.response.WorkspaceSliceResponse;
import work.managerbe.workspace.mapper.WorkspaceMapper;
import work.managerbe.workspace.repository.WorkspaceRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceServiceImpl implements WorkspaceService{

    private static final int WORKSPACE_PAGE_SIZE = 10;

    private final WorkspaceRepository workspaceRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceMapper mapper;

    @Override
    public WorkspaceResponse create(UUID userId, String code, UUID requesterId, WorkspaceCreateRequest request) {
        if(request == null) {
            throw CommonException.of(ErrorCode.INVALID_REQUEST);
        }
        userIdValidation(userId);
        userIdValidation(requesterId);
        projectCodeValidation(code);
        projectCreatorPermissionValidation(userId, requesterId);

        Project project = projectRepository.findByCreatorIdAndCodeForUpdate(userId, code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

        Workspace workspace = Workspace.create(project, request.title(), request.content());
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return mapper.toResponse(savedWorkspace);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID userId, String code, Long workspaceId, UUID requesterId) {
        userIdValidation(userId);
        userIdValidation(requesterId);
        projectCodeValidation(code);
        projectCreatorPermissionValidation(userId, requesterId);

        Workspace workspace = workspaceRepository.findByProjectPath(workspaceId, userId, code)
                .orElseThrow(() -> WorkspaceException.of(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        return mapper.toResponse(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceSliceResponse getAll(UUID userId, String code, UUID requesterId, int page) {
        userIdValidation(userId);
        userIdValidation(requesterId);
        projectCodeValidation(code);
        projectCreatorPermissionValidation(userId, requesterId);
        if (page < 0) {
            throw CommonException.of(ErrorCode.INVALID_REQUEST);
        }

        Pageable pageable = PageRequest.of(page, WORKSPACE_PAGE_SIZE);

        Slice<WorkspaceResponse> workspaces = workspaceRepository
                .findAllByProjectPath(userId, code, pageable)
                .map(mapper::toResponse);

        return mapper.toSliceResponse(workspaces);
    }

    private static void projectCreatorPermissionValidation(UUID creatorId, UUID requesterId) {
        if (!creatorId.equals(requesterId)) {
            throw CommonException.of(ErrorCode.FORBIDDEN);
        }
    }

    private void userIdValidation(UUID userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw UserException.of(UserErrorCode.USER_NOT_FOUND);
        }
    }

    private static void projectCodeValidation(String code) {
        if(code == null || code.isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_INVALID_CODE);
        }
    }
}
