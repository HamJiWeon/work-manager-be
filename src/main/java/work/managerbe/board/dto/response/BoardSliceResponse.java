package work.managerbe.board.dto.response;

import java.util.List;
import org.springframework.data.domain.Slice;
import work.managerbe.board.domain.Board;

/**
 * 보드 목록과 요청 페이지, 크기 및 다음 데이터 존재 여부를 반환한다.
 */
public record BoardSliceResponse(
        List<BoardResponse> items,
        int page,
        int size,
        boolean hasNext
) {
    /**
     * 저장소의 슬라이스를 보드 응답 목록과 다음 데이터 존재 여부로 변환한다.
     */
    public static BoardSliceResponse from(Slice<Board> boards) {
        return new BoardSliceResponse(boards.getContent().stream().map(BoardResponse::from).toList(),
                boards.getNumber(), boards.getSize(), boards.hasNext());
    }
}
