package work.managerbe.global.board;

import java.util.Map;
import work.managerbe.global.exception.GlobalException;

/**
 * BoardErrorCode만 받는 팩터리로 오류 코드의 도메인을 제한한다.
 */
public final class BoardException extends GlobalException {
    private BoardException(BoardErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        super(errorCode, details, cause);
    }

    public static BoardException of(BoardErrorCode errorCode) {
        return new BoardException(errorCode, Map.of(), null);
    }

    public static BoardException of(BoardErrorCode errorCode, Map<String, Object> details) {
        return new BoardException(errorCode, details, null);
    }

    public static BoardException of(BoardErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        return new BoardException(errorCode, details, cause);
    }
}
