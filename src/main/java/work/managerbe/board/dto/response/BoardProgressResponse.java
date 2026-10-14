package work.managerbe.board.dto.response;

import java.time.LocalDateTime;
import work.managerbe.board.domain.Board;

/**
 * 보드 목록에 보드 정보와 카드 완료율을 함께 반환한다.
 */
public record BoardProgressResponse(
        Long id,
        Long projectId,
        String name,
        int sortOrder,
        double completionRate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BoardProgressResponse from(
            Board board, double completionRate) {
        return new BoardProgressResponse(
                board.getId(),
                board.getProject().getId(),
                board.getName(),
                board.getSortOrder(),
                completionRate,
                board.getCreatedAt(),
                board.getUpdatedAt()
        );
    }
}