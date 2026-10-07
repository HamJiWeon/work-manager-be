package work.managerbe.global.exception.card;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import work.managerbe.global.exception.ApiErrorCode;

/**
 * 카드 도메인의 오류 상태와 공개 메시지를 정의한다.
 * name은 도메인 약어와 세 자리 일련번호로 구성한다.
 */
@Getter
@RequiredArgsConstructor
public enum CardErrorCode implements ApiErrorCode {
    CARD_NOT_FOUND("CRD-001", HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다."),
    CARD_INVALID_REQUEST("CRD-002", HttpStatus.BAD_REQUEST, "카드 생성 요청이 유효하지 않습니다."),
    CARD_INVALID_UPDATE("CRD-003", HttpStatus.BAD_REQUEST, "카드 수정 요청이 유효하지 않습니다.");

    private final String name;
    private final HttpStatus httpStatus;
    private final String message;
}
