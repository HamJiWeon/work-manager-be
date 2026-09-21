package work.managerbe.board.dto.request;

/**
 * 수정할 보드 ID와 선택적인 새 이름을 전달하며 배열 위치가 최종 순서가 된다.
 */
public record BoardUpdateItem(Long boardId, String name) {
}
