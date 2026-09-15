package work.managerbe.global.exception.oauthaccount;

import java.util.Map;
import work.managerbe.global.exception.GlobalException;

/**
 * OauthAccountErrorCode만 받는 팩터리로 오류 코드의 도메인을 제한한다.
 */
public final class OauthAccountException extends GlobalException {
    private OauthAccountException(OauthAccountErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        super(errorCode, details, cause);
    }

    public static OauthAccountException of(OauthAccountErrorCode errorCode) {
        return new OauthAccountException(errorCode, Map.of(), null);
    }

    public static OauthAccountException of(OauthAccountErrorCode errorCode, Map<String, Object> details) {
        return new OauthAccountException(errorCode, details, null);
    }

    public static OauthAccountException of(OauthAccountErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        return new OauthAccountException(errorCode, details, cause);
    }
}
