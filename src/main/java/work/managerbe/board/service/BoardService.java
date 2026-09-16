package work.managerbe.board.service;

import java.util.UUID;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.dto.BoardResponse;
import work.managerbe.board.dto.BoardPageResponse;

public interface BoardService {
    /**
     * 사용자와 프로젝트를 확인하고 프로젝트의 마지막 순서에 보드를 생성한다.
     */
    BoardResponse create(UUID userId, String code, BoardCreateRequest request);

    /**
     * 활성 멤버의 프로젝트 보드를 정렬 순서와 ID 오름차순으로 페이지 조회한다.
     */
    BoardPageResponse getAll(UUID userId, String code, int page, int size);
}
