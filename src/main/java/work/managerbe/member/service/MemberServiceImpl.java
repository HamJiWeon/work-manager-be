package work.managerbe.member.service;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.exception.CommonException;
import work.managerbe.global.exception.ErrorCode;
import work.managerbe.global.exception.member.*;
import work.managerbe.global.exception.project.*;
import work.managerbe.global.exception.user.*;
import work.managerbe.member.domain.Member;
import work.managerbe.member.dto.*;
import work.managerbe.member.repository.MemberRepository;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.repository.UserRepository;

/** 활성 멤버의 조회를 허용하고 OWNER의 변경 요청을 프로젝트 행 잠금으로 직렬화한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {
    private static final String OWNER_ROLE = "OWNER";
    private static final int MAX_PAGE_SIZE = 100;
    private final MemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    /** OWNER 권한과 중복 이력을 검사한 뒤 새 참여자를 저장한다. 재가입은 충돌로 처리한다. */
    @Override
    @Transactional
    public MemberResponse create(UUID creatorId, Long projectId, UUID requesterId, MemberCreateRequest request) {
        if (request == null || request.userId() == null) {
            throw CommonException.of(ErrorCode.INVALID_REQUEST);
        }
        Member.validateAssignableRole(request.role());
        Project project = findProject(creatorId, projectId, true);
        authorize(requesterId, projectId, true);
        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> UserException.of(UserErrorCode.USER_NOT_FOUND));
        if (memberRepository.existsByUser_IdAndProject_Id(request.userId(), projectId)) {
            throw MemberException.of(MemberErrorCode.MEMBER_DUPLICATE);
        }
        return MemberResponse.from(memberRepository.saveAndFlush(Member.create(user, project, request.role())));
    }

    /** 활성 요청자에게 같은 프로젝트의 활성 멤버만 반환한다. */
    @Override
    public MemberResponse get(UUID creatorId, Long projectId, UUID requesterId, Long memberId) {
        findProject(creatorId, projectId, false);
        authorize(requesterId, projectId, false);
        return MemberResponse.from(findMember(projectId, memberId));
    }

    /** 가입 시각과 ID 오름차순으로 활성 멤버를 조회하고 전체 페이지 정보를 반환한다. */
    @Override
    public MemberPageResponse getAll(UUID creatorId, Long projectId, UUID requesterId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw CommonException.of(ErrorCode.INVALID_REQUEST);
        }
        findProject(creatorId, projectId, false);
        authorize(requesterId, projectId, false);
        return MemberPageResponse.from(memberRepository.findAllByProject_IdAndLeftAtIsNull(projectId,
                PageRequest.of(page, size, Sort.by("joinedAt", "id"))));
    }

    /** OWNER가 일반 멤버의 역할만 변경하도록 검사하고 감사 시각 갱신 후 응답한다. */
    @Override
    @Transactional
    public MemberResponse update(UUID creatorId, Long projectId, UUID requesterId, Long memberId,
                                 MemberUpdateRequest request) {
        if (request == null) {
            throw CommonException.of(ErrorCode.INVALID_REQUEST);
        }
        Member.validateAssignableRole(request.role());
        findProject(creatorId, projectId, true);
        authorize(requesterId, projectId, true);
        Member member = findMember(projectId, memberId);
        member.changeRole(request.role());
        memberRepository.flush();
        return MemberResponse.from(member);
    }

    /** OWNER 자신의 삭제를 막고 탈퇴 시각만 기록하여 카드 담당자와 참여 이력을 유지한다. */
    @Override
    @Transactional
    public void delete(UUID creatorId, Long projectId, UUID requesterId, Long memberId) {
        findProject(creatorId, projectId, true);
        authorize(requesterId, projectId, true);
        Member member = findMember(projectId, memberId);
        if (OWNER_ROLE.equals(member.getRole())) {
            throw MemberException.of(MemberErrorCode.MEMBER_OWNER_PROTECTED);
        }
        member.leave(LocalDateTime.now());
        memberRepository.flush();
    }

    /** 경로의 생성자와 프로젝트 조합을 검증하며 변경 요청은 프로젝트 행을 잠근다. */
    private Project findProject(UUID creatorId, Long projectId, boolean lock) {
        if (creatorId == null || projectId == null) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        Project project = (lock ? projectRepository.findByIdForUpdate(projectId) : projectRepository.findById(projectId))
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
        if (!creatorId.equals(project.getCreator().getId())) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    /** 경로의 사용자 ID 대신 인증 principal의 활성 참여 및 OWNER 역할을 검사한다. */
    private void authorize(UUID requesterId, Long projectId, boolean ownerOnly) {
        if (requesterId == null) {
            throw CommonException.of(ErrorCode.UNAUTHORIZED);
        }
        Member requester = memberRepository.findByUser_IdAndProject_IdAndLeftAtIsNull(requesterId, projectId)
                .orElseThrow(() -> new AccessDeniedException("활성 멤버만 접근할 수 있습니다."));
        if (ownerOnly && !OWNER_ROLE.equals(requester.getRole())) {
            throw new AccessDeniedException("OWNER만 멤버를 변경할 수 있습니다.");
        }
    }

    /** 다른 프로젝트 또는 탈퇴한 멤버는 모두 MEMBER_NOT_FOUND로 처리한다. */
    private Member findMember(Long projectId, Long memberId) {
        if (memberId == null) {
            throw MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        return memberRepository.findByIdAndProject_IdAndLeftAtIsNull(memberId, projectId)
                .orElseThrow(() -> MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
