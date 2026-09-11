package work.managerbe.global.exception;

import java.util.Map;

/**
 * ErrorCode만 받는 팩터리로 오류 코드의 도메인을 제한한다.
 */
public final class CommonException extends GlobalException {
    private CommonException(ErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        super(errorCode, details, cause);
    }

    public static CommonException of(ErrorCode errorCode) {
        return new CommonException(errorCode, Map.of(), null);
    }

    public static CommonException of(ErrorCode errorCode, Map<String, Object> details) {
        return new CommonException(errorCode, details, null);
    }

    public static CommonException of(ErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        return new CommonException(errorCode, details, cause);
    }
}
