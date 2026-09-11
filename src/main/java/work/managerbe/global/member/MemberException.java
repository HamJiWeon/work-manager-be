package work.managerbe.global.member;

import java.util.Map;
import work.managerbe.global.exception.GlobalException;

/**
 * MemberErrorCode만 받는 팩터리로 오류 코드의 도메인을 제한한다.
 */
public final class MemberException extends GlobalException {
    private MemberException(MemberErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        super(errorCode, details, cause);
    }

    public static MemberException of(MemberErrorCode errorCode) {
        return new MemberException(errorCode, Map.of(), null);
    }

    public static MemberException of(MemberErrorCode errorCode, Map<String, Object> details) {
        return new MemberException(errorCode, details, null);
    }

    public static MemberException of(MemberErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        return new MemberException(errorCode, details, cause);
    }
}
