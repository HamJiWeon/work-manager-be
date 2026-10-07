package work.managerbe.card.dto.response;

import work.managerbe.card.domain.Card;
import work.managerbe.card.domain.CardStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 카드 내용과 일정, 담당자·프로젝트·보드 ID를 반환한다.
 */
public record CardResponse(
        Long id,
        String username,
        String title,
        String content,
        CardStatus status,
        Long memberId,
        Long projectId,
        Long boardId,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int sortOrder
) {
    /**
     * 카드 엔티티를 응답으로 변환하고 연관 엔티티는 ID로 표현한다.
     */
    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getUsername(),
                card.getTitle(),
                card.getContent(),
                card.getStatus(),
                card.getMember() == null ? null : card.getMember().getId(),
                card.getProject() == null ? null : card.getProject().getId(),
                card.getBoard() == null ? null : card.getBoard().getId(),
                card.getStartDate(),
                card.getEndDate(),
                card.getCreatedAt(),
                card.getUpdatedAt(),
                card.getSortOrder()
        );
    }
}
