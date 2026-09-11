package work.managerbe.global.exception;

import java.util.Map;

/**
 * 예외 객체와 원인 대신 공개 가능한 오류 정보만 응답한다.
 */
public record ErrorResponse(String code, String message, Map<String, Object> details) {
    public static ErrorResponse of(ApiErrorCode errorCode, Map<String, Object> details) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(),
                details == null ? Map.of() : Map.copyOf(details));
    }
}
