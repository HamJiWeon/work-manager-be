package work.managerbe.board.dto.response;

/**
 * 보드별 카드 집계 결과를 전달한다.
 */
public record BoardProgressRow(
        Long boardId,
        long totalCardCount,
        long doneCardCount
) {
}