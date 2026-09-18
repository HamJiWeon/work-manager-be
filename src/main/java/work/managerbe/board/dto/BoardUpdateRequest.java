package work.managerbe.board.dto;

/**
 * null인 필드는 유지하고 전달된 이름과 0부터 시작하는 순서만 수정한다.
 */
public record BoardUpdateRequest(String name, Integer sortOrder) {
}
