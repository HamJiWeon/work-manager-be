package work.managerbe.global.exception.workspace;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import work.managerbe.global.exception.ApiErrorCode;

/**
 * 워크스페이스 도메인의 오류 상태와 공개 메시지를 정의한다.
 * name은 도메인 약어와 세 자리 일련번호로 구성한다.
 */
@Getter
@RequiredArgsConstructor
public enum WorkspaceErrorCode implements ApiErrorCode {
    WORKSPACE_NOT_FOUND("WSP-001", HttpStatus.NOT_FOUND, "워크스페이스를 찾을 수 없습니다.");

    private final String name;
    private final HttpStatus httpStatus;
    private final String message;
}
