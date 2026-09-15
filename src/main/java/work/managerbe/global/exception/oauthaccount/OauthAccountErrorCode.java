package work.managerbe.global.exception.oauthaccount;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import work.managerbe.global.exception.ApiErrorCode;

/**
 * 소셜 계정 도메인의 오류 상태와 공개 메시지를 정의한다.
 * name은 도메인 약어와 세 자리 일련번호로 구성한다.
 */
@Getter
@RequiredArgsConstructor
public enum OauthAccountErrorCode implements ApiErrorCode {
    OAUTH_ACCOUNT_NOT_FOUND("OAU-001", HttpStatus.NOT_FOUND, "소셜 계정을 찾을 수 없습니다.");

    private final String name;
    private final HttpStatus httpStatus;
    private final String message;
}
