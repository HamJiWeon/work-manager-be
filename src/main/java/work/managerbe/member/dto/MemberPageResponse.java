package work.managerbe.member.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import work.managerbe.member.domain.Member;

/** 활성 멤버 목록과 요청 페이지, 크기 및 전체 멤버·페이지 수를 반환한다. */
public record MemberPageResponse(
        List<MemberResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /** 저장소의 페이지를 기존 멤버 응답 목록과 전체 개수 정보로 변환한다. */
    public static MemberPageResponse from(Page<Member> members) {
        return new MemberPageResponse(members.getContent().stream().map(MemberResponse::from).toList(),
                members.getNumber(), members.getSize(), members.getTotalElements(), members.getTotalPages());
    }
}
