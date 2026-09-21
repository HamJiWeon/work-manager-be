package work.managerbe.project.service;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.exception.CommonException;
import work.managerbe.global.exception.ErrorCode;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.request.ProjectCreateRequest;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.dto.response.ProjectSliceResponse;
import work.managerbe.project.mapper.ProjectMapper;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.member.domain.Member;
import work.managerbe.member.repository.MemberRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService{

    private static final String CREATOR_ROLE = "OWNER";
    private static final int PROJECT_PAGE_SIZE = 10;

    private final ProjectRepository projectRepository;
    private final ProjectMapper mapper;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    /**
     * 생성자 존재를 검증하고 프로젝트와 생성자의 활성 멤버 관계를 같은 트랜잭션에 저장한다.
     */
    @Override
    @Transactional
    public ProjectResponse create(UUID userId ,ProjectCreateRequest request) {
        projectCodeValidation(request);
        projectNameValidation(request);
        userIdValidation(userId);
        if(request.code().contains("_")) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_INVALID_CODE_FORMAT);
        }


        User creator = userRepository.findById(userId)
                .orElseThrow(() -> UserException.of(UserErrorCode.USER_NOT_FOUND));

        String code = request.code().toUpperCase(Locale.ROOT);
        if (projectRepository.existsByCreator_IdAndCode(userId, code)) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_DUPLICATE_CODE);
        }

        Project project = Project.create(creator, code, request.name(), request.description());
        Project savedProject;
        try {
            savedProject = projectRepository.saveAndFlush(project);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException violation
                    && "uk_projects_user_code".equalsIgnoreCase(
                    violation.getConstraintName())) {
                throw ProjectException.of(ProjectErrorCode.PROJECT_DUPLICATE_CODE);
            }
            throw e;
        }
        memberRepository.save(Member.create(creator, savedProject, CREATOR_ROLE));
        return mapper.toResponse(savedProject);
    }

    @Override
    public ProjectResponse get(UUID creatorId, String code, UUID requesterId) {
        userIdValidation(creatorId);
        userIdValidation(requesterId);
        if(code == null || code.isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_INVALID_CODE);
        }

        Project project = projectRepository
                .findAccessibleProject(creatorId, code, requesterId)
                .orElseThrow(() ->
                        ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));


        return mapper.toResponse(project);
    }

    @Override
    public ProjectSliceResponse getAll(UUID pathUserId, UUID requesterId, int page) {
        userIdValidation(pathUserId);
        userIdValidation(requesterId);
        if (!pathUserId.equals(requesterId)) {
            throw CommonException.of(ErrorCode.FORBIDDEN);
        }
        if (page < 0) {
            throw CommonException.of(ErrorCode.INVALID_REQUEST);
        }

        Pageable pageable = PageRequest.of(page, PROJECT_PAGE_SIZE);

        Slice<ProjectResponse> projects = projectRepository
                .findActiveProjects(requesterId, pageable)
                .map(mapper::toResponse);

        return mapper.toSliceResponse(projects);
    }

    private static void userIdValidation(UUID userId) {
        if (userId == null) {
            throw UserException.of(UserErrorCode.USER_NOT_FOUND);
        }
    }

    private static void projectNameValidation(ProjectCreateRequest request) {
        if(request.name() == null || request.name().isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_INVALID_NAME);
        }
    }

    private static void projectCodeValidation(ProjectCreateRequest request) {
        if(request.code() == null || request.code().isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_INVALID_CODE);
        }
    }
}
