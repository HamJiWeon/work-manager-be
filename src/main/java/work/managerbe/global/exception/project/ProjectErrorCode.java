package work.managerbe.global.exception.project;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import work.managerbe.global.exception.ApiErrorCode;

/**
 * 프로젝트 도메인의 오류 상태와 공개 메시지를 정의한다.
 * name은 도메인 약어와 세 자리 일련번호로 구성한다.
 */
@Getter
@RequiredArgsConstructor
public enum ProjectErrorCode implements ApiErrorCode {
    PROJECT_NOT_FOUND("PJT-001", HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."),
    PROJECT_INVALID_CODE("PJT-002", HttpStatus.BAD_REQUEST, "프로젝트 코드는 필수입니다."),
    PROJECT_INVALID_NAME("PJT-003", HttpStatus.BAD_REQUEST, "프로젝트 이름은 필수입니다."),
    PROJECT_DUPLICATE_PREFIX("PJT-004", HttpStatus.CONFLICT, "사용자별 코드 접두사는 중복 금지입니다."),
    PROJECT_INVALID_PREFIX("PJT-005", HttpStatus.BAD_REQUEST, "코드 접두사에는 '_' 금지입니다.");

    private final String name;
    private final HttpStatus httpStatus;
    private final String message;
}
