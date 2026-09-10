package work.managerbe.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공통 오류의 HTTP 상태와 사용자 메시지를 정의한다.
 * enum 이름을 오류 식별자로 사용하여 같은 HTTP 상태의 오류도 구분한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode implements ApiErrorCode {

    INVALID_REQUEST("GLB-001", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED("GLB-002", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN("GLB-003", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND("GLB-004", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT("GLB-005", HttpStatus.CONFLICT, "요청이 현재 리소스 상태와 충돌합니다."),
    INTERNAL_SERVER_ERROR("GLB-006", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;
}
