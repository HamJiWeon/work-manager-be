package work.managerbe.member.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import work.managerbe.member.domain.Member;

/** 활성 멤버 목록과 전체 개수 및 페이지 수를 반환한다. */
public record MemberPageResponse(
        List<MemberResponse> items, int page, int size, long totalElements, int totalPages) {
    /** 페이지의 엔티티를 응답으로 변환하고 조회 메타데이터를 유지한다. */
    public static MemberPageResponse from(Page<Member> members) {
        return new MemberPageResponse(members.getContent().stream().map(MemberResponse::from).toList(),
                members.getNumber(), members.getSize(), members.getTotalElements(), members.getTotalPages());
    }
}
