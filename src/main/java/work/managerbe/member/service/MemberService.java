package work.managerbe.member.service;

import java.util.UUID;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.member.dto.request.MemberCreateRequest;

public interface MemberService {
    /** 프로젝트의 활성 OWNER 또는 ADMIN이 등록된 사용자를 일반 멤버로 추가한다. */
    MemberResponse create(UUID creatorId, String code, UUID requesterId, MemberCreateRequest request);
}
