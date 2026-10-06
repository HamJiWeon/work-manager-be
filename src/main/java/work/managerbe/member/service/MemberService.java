package work.managerbe.member.service;

import java.util.UUID;
import work.managerbe.member.dto.*;

/** 인증된 요청자를 기준으로 프로젝트의 멤버 CRUD를 처리한다. */
public interface MemberService {
    MemberResponse create(UUID creatorId, Long projectId, UUID requesterId, MemberCreateRequest request);
    MemberResponse get(UUID creatorId, Long projectId, UUID requesterId, Long memberId);
    MemberPageResponse getAll(UUID creatorId, Long projectId, UUID requesterId, int page, int size);
    MemberResponse update(UUID creatorId, Long projectId, UUID requesterId, Long memberId, MemberUpdateRequest request);
    void delete(UUID creatorId, Long projectId, UUID requesterId, Long memberId);
}
