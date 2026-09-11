package work.managerbe.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 공통 및 도메인 오류 enum이 제공해야 하는 응답 정보를 정의한다.
 */
public interface ApiErrorCode {
    String name();
    HttpStatus getHttpStatus();
    String getMessage();
}
