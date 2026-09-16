package work.managerbe.board.service;

import java.util.UUID;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.dto.BoardResponse;

public interface BoardService {
    /**
     * 사용자와 프로젝트를 확인하고 프로젝트의 마지막 순서에 보드를 생성한다.
     */
    BoardResponse create(UUID userId, String code, BoardCreateRequest request);
}
