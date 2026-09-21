package work.managerbe.board.service;

import java.util.List;
import java.util.UUID;
import work.managerbe.board.dto.request.BoardCreateRequest;
import work.managerbe.board.dto.response.BoardResponse;
import work.managerbe.board.dto.request.BoardUpdateRequest;
import work.managerbe.board.dto.response.BoardSliceResponse;

public interface BoardService {
    /**
     * 사용자와 프로젝트를 확인하고 프로젝트의 마지막 순서에 보드를 생성한다.
     */
    BoardResponse create(UUID creatorId, String code, UUID requesterId, BoardCreateRequest request);

    /**
     * 활성 멤버의 프로젝트 보드를 정렬 순서와 ID 오름차순으로 페이지 조회한다.
     */
    BoardSliceResponse getAll(UUID creatorId, String code, UUID requesterId, int page, int size);

    /**
     * 활성 멤버가 전달한 배열 순서대로 전체 보드를 재배치하고 이름을 함께 수정한다.
     */
    List<BoardResponse> update(UUID creatorId, String code, UUID requesterId, BoardUpdateRequest request);
}
