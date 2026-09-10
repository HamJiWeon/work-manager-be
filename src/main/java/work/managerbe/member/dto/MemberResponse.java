package work.managerbe.member.dto;

import work.managerbe.member.domain.Member;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 참여자 정보와 연관 사용자·프로젝트의 ID를 반환한다.
 */
public record MemberResponse(
        Long id,
        UUID userId,
        Long projectId,
        String role,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 참여자 엔티티를 응답으로 변환하며 연관 엔티티 자체는 노출하지 않는다.
     */
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getUser() == null ? null : member.getUser().getId(),
                member.getProject() == null ? null : member.getProject().getId(),
                member.getRole(),
                member.getJoinedAt(),
                member.getLeftAt(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
