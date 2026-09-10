package work.managerbe.global.exception;

import java.util.Map;
import java.util.Objects;
import lombok.Getter;

/**
 * 도메인 오류 코드와 공개 가능한 상세 정보를 보관하고 원인 예외를 보존한다.
 * 상세 정보는 불변 맵으로 복사하며 cause는 응답 데이터와 분리한다.
 */
@Getter
public abstract class GlobalException extends RuntimeException {
    private final ApiErrorCode errorCode;
    private final Map<String, Object> details;

    protected GlobalException(ApiErrorCode errorCode, Map<String, Object> details) {
        this(errorCode, details, null);
    }

    protected GlobalException(ApiErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode는 필수입니다.").getMessage(), cause);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }
}
