package work.managerbe.board.dto;

import work.managerbe.board.domain.Board;

import java.time.LocalDateTime;

/**
 * 보드 속성과 소속 프로젝트 ID를 반환한다.
 */
public record BoardResponse(
        Long id,
        Long projectId,
        String name,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 보드 엔티티를 응답으로 변환하며 프로젝트는 ID로 표현한다.
     */
    public static BoardResponse from(Board board) {
        return new BoardResponse(
                board.getId(),
                board.getProject() == null ? null : board.getProject().getId(),
                board.getName(),
                board.getSortOrder(),
                board.getCreatedAt(),
                board.getUpdatedAt()
        );
    }
}
