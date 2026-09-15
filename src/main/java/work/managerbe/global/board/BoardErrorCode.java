package work.managerbe.global.board;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import work.managerbe.global.exception.ApiErrorCode;

/**
 * 보드 도메인의 오류 상태와 공개 메시지를 정의한다.
 * name은 도메인 약어와 세 자리 일련번호로 구성한다.
 */
@Getter
@RequiredArgsConstructor
public enum BoardErrorCode implements ApiErrorCode {
    BOARD_NOT_FOUND("BRD-001", HttpStatus.NOT_FOUND, "보드를 찾을 수 없습니다."),
    BOARD_INVALID_NAME("BRD-002", HttpStatus.BAD_REQUEST, "보드 이름은 필수입니다."),
    BOARD_SORT_ORDER_EXHAUSTED("BRD-003", HttpStatus.CONFLICT, "보드 정렬 순서의 최댓값에 도달했습니다.");

    private final String name;
    private final HttpStatus httpStatus;
    private final String message;
}
