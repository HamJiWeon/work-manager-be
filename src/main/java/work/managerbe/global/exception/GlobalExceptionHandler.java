package work.managerbe.global.exception;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 도메인 예외와 MVC 예외를 공통 응답으로 변환한다.
 * MVC가 결정한 상태와 헤더는 유지하며 예상하지 못한 오류의 내부 메시지는 숨긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<Object> handleGlobalException(GlobalException exception) {
        ApiErrorCode errorCode = exception.getErrorCode();
        if (errorCode.getHttpStatus().is5xxServerError() || exception.getCause() != null) {
            log.error("도메인 예외: {}", errorCode.name(), exception);
        }
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, exception.getDetails()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException exception) {
        return response(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException exception) {
        return response(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception) {
        log.error("처리되지 않은 서버 오류", exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode errorCode = resolveErrorCode(statusCode);
        if (statusCode.is5xxServerError()) {
            log.error("MVC 서버 오류", exception);
        }
        return super.handleExceptionInternal(exception, ErrorResponse.of(errorCode, Map.of()),
                headers, statusCode, request);
    }

    private static ResponseEntity<Object> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, Map.of()));
    }

    private static ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
        if (statusCode.is5xxServerError()) {
            return ErrorCode.INTERNAL_SERVER_ERROR;
        }
        return switch (HttpStatus.resolve(statusCode.value())) {
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case CONFLICT -> ErrorCode.CONFLICT;
            case null, default -> ErrorCode.INVALID_REQUEST;
        };
    }
}
