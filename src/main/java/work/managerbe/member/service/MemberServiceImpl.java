package work.managerbe.member.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.exception.member.MemberErrorCode;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.member.domain.Member;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.member.dto.request.MemberCreateRequest;
import work.managerbe.member.repository.MemberRepository;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    /**
     * 생성자 권한을 확인하고 프로젝트 행 잠금 안에서 중복 가입을 검사한다.
     * 탈퇴 이력이 있는 사용자도 중복으로 처리하며 저장 후 가입·감사 시각을 응답한다.
     */
    @Override
    public MemberResponse create(UUID creatorId, String code, UUID requesterId, MemberCreateRequest request) {
        validateRequest(request);
        validateRequester(requesterId);
        if (creatorId == null || code == null || code.isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        if (!creatorId.equals(requesterId)) {
            throw new AccessDeniedException("프로젝트 생성자만 멤버를 추가할 수 있습니다.");
        }

        Project project = projectRepository.findByCreatorIdAndCodeForUpdate(creatorId, code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> UserException.of(UserErrorCode.USER_NOT_FOUND));
        if (memberRepository.existsByUser_IdAndProject_Id(user.getId(), project.getId())) {
            throw MemberException.of(MemberErrorCode.MEMBER_ALREADY_EXISTS);
        }

        Member member = Member.create(user, project, request.role());
        return MemberResponse.from(memberRepository.saveAndFlush(member));
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
