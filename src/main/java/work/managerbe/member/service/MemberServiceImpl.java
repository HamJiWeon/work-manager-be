package work.managerbe.member.service;

import work.managerbe.member.domain.MemberRole;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.exception.member.MemberErrorCode;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.member.domain.Member;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.member.dto.MemberPageResponse;
import work.managerbe.member.dto.request.MemberCreateRequest;
import work.managerbe.member.dto.request.MemberUpdateRequest;
import work.managerbe.member.repository.MemberRepository;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberServiceImpl implements MemberService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_PAGE_OFFSET = Integer.MAX_VALUE;
    private static final Set<MemberRole> MEMBER_MANAGER_ROLES = Set.of(MemberRole.OWNER, MemberRole.ADMIN);

    private final MemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    /** 프로젝트 행 잠금 안에서 활성 OWNER 권한을 확인하고 역할 변경을 flush한 뒤 감사 시각까지 응답한다. */
    @Override
    public MemberResponse update(UUID creatorId, String code, Long memberId, UUID requesterId,
                                 MemberUpdateRequest request) {
        if (request == null || !request.isValid()) {
            throw MemberException.of(MemberErrorCode.MEMBER_INVALID_ROLE);
        }
        Project project = findOwnerProjectForUpdate(creatorId, code, requesterId);
        Member member = findActiveMember(memberId, project.getId());
        member.changeRole(request.role());
        memberRepository.flush();
        return MemberResponse.from(member);
    }

    /** 카드 생성과 같은 프로젝트 잠금을 사용하고 최초 탈퇴 시각과 카드 담당자 관계를 보존한다. */
    @Override
    public void delete(UUID creatorId, String code, Long memberId, UUID requesterId) {
        Project project = findOwnerProjectForUpdate(creatorId, code, requesterId);
        if (memberId == null) {
            throw MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        Member member = memberRepository.findByIdAndProject_Id(memberId, project.getId())
                .orElseThrow(() -> MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND));
        if (member.getRole() == MemberRole.OWNER) {
            throw MemberException.of(MemberErrorCode.MEMBER_OWNER_PROTECTED);
        }
        if (member.getLeftAt() == null) {
            member.leave(LocalDateTime.now());
        }
    }

    /** 생성자·코드로 프로젝트를 잠그고 해당 프로젝트의 탈퇴하지 않은 OWNER인지 검증한다. */
    private Project findOwnerProjectForUpdate(UUID creatorId, String code, UUID requesterId) {
        validateRequester(requesterId);
        if (creatorId == null || code == null || code.isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        Project project = projectRepository.findByCreatorIdAndCodeForUpdate(creatorId, code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
        if (!memberRepository.existsByUser_IdAndProject_IdAndLeftAtIsNullAndRoleIn(
                requesterId, project.getId(), Set.of(MemberRole.OWNER))) {
            throw new AccessDeniedException("프로젝트의 활성 OWNER만 멤버를 변경하거나 삭제할 수 있습니다.");
        }
        return project;
    }

    /** 멤버 ID와 프로젝트 소속, 활성 상태를 함께 확인해 탈퇴 이력과 다른 프로젝트의 멤버를 숨긴다. */
    private Member findActiveMember(Long memberId, Long projectId) {
        if (memberId == null) {
            throw MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        return memberRepository.findByIdAndProject_IdAndLeftAtIsNull(memberId, projectId)
                .orElseThrow(() -> MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /** 페이지 조건과 JPA 오프셋 한계를 검증하고 읽기 전용 트랜잭션에서 활성 멤버 목록과 집계를 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public MemberPageResponse getAll(UUID creatorId, String code, UUID requesterId, int page, int size) {
        validatePage(page, size);
        Project project = findReadableProject(creatorId, code, requesterId);
        return MemberPageResponse.from(memberRepository.findAllByProject_IdAndLeftAtIsNullOrderByJoinedAtAscIdAsc(
                project.getId(), PageRequest.of(page, size)));
    }

    /** 서비스 직접 호출도 페이지 범위를 검증하며 long 곱셈으로 오버플로 없이 JPA 한계를 확인한다. */
    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > MAX_PAGE_OFFSET) {
            throw MemberException.of(MemberErrorCode.MEMBER_INVALID_PAGE);
        }
    }

    /**
     * 읽기 전용 트랜잭션에서 프로젝트의 활성 참여를 확인하고 프로젝트에 속한 멤버만 조회한다.
     * 탈퇴한 대상과 없는 ID, 다른 프로젝트의 ID는 모두 멤버 없음으로 처리한다.
     */
    @Override
    @Transactional(readOnly = true)
    public MemberResponse get(UUID creatorId, String code, Long memberId, UUID requesterId) {
        Project project = findReadableProject(creatorId, code, requesterId);
        return MemberResponse.from(findActiveMember(memberId, project.getId()));
    }

    /** 단건과 목록 조회에서 생성자·코드로 프로젝트를 식별하고 요청자의 활성 참여를 검증한다. */
    private Project findReadableProject(UUID creatorId, String code, UUID requesterId) {
        validateRequester(requesterId);
        if (creatorId == null || code == null || code.isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        Project project = projectRepository.findByCreator_IdAndCode(creatorId, code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
        if (!memberRepository.existsByUserIdAndProjectId(requesterId, project.getId())) {
            throw new AccessDeniedException("프로젝트의 활성 멤버만 멤버를 조회할 수 있습니다.");
        }
        return project;
    }

    /**
     * 프로젝트 행 잠금 안에서 활성 OWNER·ADMIN 권한과 중복 가입을 검사한다.
     * 탈퇴 이력이 있는 사용자도 중복으로 처리하며 저장 후 가입·감사 시각을 응답한다.
     */
    @Override
    public MemberResponse create(UUID creatorId, String code, UUID requesterId, MemberCreateRequest request) {
        validateRequest(request);
        validateRequester(requesterId);
        if (creatorId == null || code == null || code.isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        Project project = projectRepository.findByCreatorIdAndCodeForUpdate(creatorId, code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
        validateMemberManager(requesterId, project.getId());
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> UserException.of(UserErrorCode.USER_NOT_FOUND));
        if (memberRepository.existsByUser_IdAndProject_Id(user.getId(), project.getId())) {
            throw MemberException.of(MemberErrorCode.MEMBER_ALREADY_EXISTS);
        }

        Member member = Member.create(user, project, request.role());
        return MemberResponse.from(memberRepository.saveAndFlush(member));
    }

    /** 해당 프로젝트의 탈퇴하지 않은 OWNER 또는 ADMIN만 멤버를 추가할 수 있다. */
    private void validateMemberManager(UUID requesterId, Long projectId) {
        if (!memberRepository.existsByUser_IdAndProject_IdAndLeftAtIsNullAndRoleIn(
                requesterId, projectId, MEMBER_MANAGER_ROLES)) {
            throw new AccessDeniedException("프로젝트의 활성 OWNER 또는 ADMIN만 멤버를 추가할 수 있습니다.");
        }
    }

    /** 본문과 사용자 ID, 허용된 역할을 저장 전에 검증한다. */
    private static void validateRequest(MemberCreateRequest request) {
        if (request == null || !request.isValid()) {
            throw MemberException.of(MemberErrorCode.MEMBER_INVALID_REQUEST);
        }
    }

    /** 인증된 요청자가 실제 등록된 사용자인지 확인한다. */
    private void validateRequester(UUID requesterId) {
        if (requesterId == null || !userRepository.existsById(requesterId)) {
            throw UserException.of(UserErrorCode.USER_NOT_FOUND);
        }
    }
}
