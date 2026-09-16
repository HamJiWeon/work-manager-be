package work.managerbe.global.exception.user;

import java.util.Map;
import work.managerbe.global.exception.GlobalException;

/**
 * UserErrorCode만 받는 팩터리로 오류 코드의 도메인을 제한한다.
 */
public final class UserException extends GlobalException {
    private UserException(UserErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        super(errorCode, details, cause);
    }

    public static UserException of(UserErrorCode errorCode) {
        return new UserException(errorCode, Map.of(), null);
    }

    public static UserException of(UserErrorCode errorCode, Map<String, Object> details) {
        return new UserException(errorCode, details, null);
    }

    public static UserException of(UserErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        return new UserException(errorCode, details, cause);
    }
}
