package work.managerbe.board.dto.response;

import java.util.List;
import org.springframework.data.domain.Slice;

/**
 * 보드 목록과 요청 페이지, 크기 및 다음 데이터 존재 여부를 반환한다.
 */
public record BoardSliceResponse(
        List<BoardProgressResponse> items,
        int page,
        int size,
        boolean hasNext
) {
    /**
     * 완료율이 포함된 보드 응답과 페이지 정보를 반환한다.
     */
    public static BoardSliceResponse from(Slice<BoardProgressResponse> boards) {
        return new BoardSliceResponse(
                boards.getContent(),
                boards.getNumber(),
                boards.getSize(),
                boards.hasNext()
        );
    }
}
