package work.managerbe.member.service;

import java.util.UUID;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.member.dto.MemberPageResponse;
import work.managerbe.member.dto.request.MemberCreateRequest;

public interface MemberService {
    /** 프로젝트의 활성 멤버가 활성 참여자 목록을 가입 시각과 ID 오름차순으로 조회한다. */
    MemberPageResponse getAll(UUID creatorId, String code, UUID requesterId, int page, int size);

    /** 프로젝트의 활성 멤버가 같은 프로젝트의 멤버를 탈퇴 이력을 포함해 조회한다. */
    MemberResponse get(UUID creatorId, String code, Long memberId, UUID requesterId);

    /** 프로젝트의 활성 OWNER 또는 ADMIN이 등록된 사용자를 일반 멤버로 추가한다. */
    MemberResponse create(UUID creatorId, String code, UUID requesterId, MemberCreateRequest request);
}
