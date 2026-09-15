package work.managerbe.global.exception.card;

import java.util.Map;
import work.managerbe.global.exception.GlobalException;

/**
 * CardErrorCode만 받는 팩터리로 오류 코드의 도메인을 제한한다.
 */
public final class CardException extends GlobalException {
    private CardException(CardErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        super(errorCode, details, cause);
    }

    public static CardException of(CardErrorCode errorCode) {
        return new CardException(errorCode, Map.of(), null);
    }

    public static CardException of(CardErrorCode errorCode, Map<String, Object> details) {
        return new CardException(errorCode, details, null);
    }

    public static CardException of(CardErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        return new CardException(errorCode, details, cause);
    }
}
