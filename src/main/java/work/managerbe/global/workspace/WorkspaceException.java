package work.managerbe.global.workspace;

import java.util.Map;
import work.managerbe.global.exception.GlobalException;

/**
 * WorkspaceErrorCode만 받는 팩터리로 오류 코드의 도메인을 제한한다.
 */
public final class WorkspaceException extends GlobalException {
    private WorkspaceException(WorkspaceErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        super(errorCode, details, cause);
    }

    public static WorkspaceException of(WorkspaceErrorCode errorCode) {
        return new WorkspaceException(errorCode, Map.of(), null);
    }

    public static WorkspaceException of(WorkspaceErrorCode errorCode, Map<String, Object> details) {
        return new WorkspaceException(errorCode, details, null);
    }

    public static WorkspaceException of(WorkspaceErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        return new WorkspaceException(errorCode, details, cause);
    }
}
