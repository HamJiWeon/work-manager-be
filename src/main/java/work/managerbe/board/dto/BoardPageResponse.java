package work.managerbe.board.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import work.managerbe.board.domain.Board;

/**
 * 보드 목록과 요청 페이지, 전체 개수 및 전체 페이지 수를 반환한다.
 */
public record BoardPageResponse(
        List<BoardResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /**
     * 저장소의 페이지를 보드 응답 목록과 페이지 메타데이터로 변환한다.
     */
    public static BoardPageResponse from(Page<Board> boards) {
        return new BoardPageResponse(boards.getContent().stream().map(BoardResponse::from).toList(),
                boards.getNumber(), boards.getSize(), boards.getTotalElements(), boards.getTotalPages());
    }
}
