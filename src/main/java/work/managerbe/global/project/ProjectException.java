package work.managerbe.global.project;

import java.util.Map;
import work.managerbe.global.exception.GlobalException;

/**
 * ProjectErrorCode만 받는 팩터리로 오류 코드의 도메인을 제한한다.
 */
public final class ProjectException extends GlobalException {
    private ProjectException(ProjectErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        super(errorCode, details, cause);
    }

    public static ProjectException of(ProjectErrorCode errorCode) {
        return new ProjectException(errorCode, Map.of(), null);
    }

    public static ProjectException of(ProjectErrorCode errorCode, Map<String, Object> details) {
        return new ProjectException(errorCode, details, null);
    }

    public static ProjectException of(ProjectErrorCode errorCode, Map<String, Object> details, Throwable cause) {
        return new ProjectException(errorCode, details, cause);
    }
}
