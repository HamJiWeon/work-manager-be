package work.managerbe.board.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 생성할 보드 이름을 받으며 누락되거나 공백뿐인 이름을 거절한다.
 */
public record BoardCreateRequest(
        @NotBlank
        String name
) {
}
