package work.managerbe.board.dto.request;

import java.util.List;

/**
 * 배열 순서를 최종 보드 순서로 사용하고 각 항목에 전달된 이름을 함께 수정한다.
 */
public record BoardUpdateRequest(List<BoardUpdateItem> boards) {
}
