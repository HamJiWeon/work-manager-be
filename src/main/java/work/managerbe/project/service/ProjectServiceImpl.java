package work.managerbe.project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.request.ProjectCreateRequest;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.mapper.ProjectMapper;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.member.domain.Member;
import work.managerbe.member.repository.MemberRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService{

    private static final String CREATOR_ROLE = "OWNER";

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
        if(request.cardPrefix() == null || request.name() == null || request.cardPrefix().isBlank() || request.name().isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_INVALID_CODE_NAME);
        }

        if (userId == null) {
            throw UserException.of(UserErrorCode.USER_NOT_FOUND);
        }
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> UserException.of(UserErrorCode.USER_NOT_FOUND));

        projectRepository.findByUser_Id(userId).stream()
                .filter(a -> a.cardPrefix(a.getCode()).equalsIgnoreCase(request.cardPrefix()))
                .findFirst()
                .ifPresent(project -> {
                    throw ProjectException.of(ProjectErrorCode.PROJECT_DUPLICATE_PREFIX);
                });

        String code = request.cardPrefix().toUpperCase() + "_" + UUID.randomUUID();
        Project project = Project.create(code, request.name(), request.description());
        Project savedProject = projectRepository.save(project);
        memberRepository.save(Member.create(creator, savedProject, CREATOR_ROLE));
        return mapper.toResponse(savedProject);
    }

    @Override
    public ProjectResponse get(UUID userId, String code) {
        if(code == null || code.isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_INVALID_CODE_NAME);
        }
        if(userId == null) {
            throw UserException.of(UserErrorCode.USER_NOT_FOUND);
        }

        Project project = projectRepository.findByUser_Id(userId).stream()
                .filter(a -> a.cardPrefix(a.getCode()).equals(code))
                .findFirst()
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

        return mapper.toResponse(project);
    }
}
