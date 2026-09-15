package work.managerbe.global.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트용 하위 타입으로 원인 없는 보호 생성자의 오류 정보 전달을 검증한다.
 */
class GlobalExceptionTest {

    @Test
    void 원인_없는_생성자는_코드와_상세정보를_보존한다() {
        // given
        Map<String, Object> details = Map.of("field", "name");

        // when
        GlobalException exception = createException(ErrorCode.INVALID_REQUEST, details);

        // then
        assertThat(exception.getErrorCode()).isSameAs(ErrorCode.INVALID_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("잘못된 요청입니다.");
        assertThat(exception.getDetails()).containsExactlyEntriesOf(details);
        assertThat(exception.getCause()).isNull();
    }

    private static GlobalException createException(ApiErrorCode errorCode, Map<String, Object> details) {
        return new GlobalException(errorCode, details) { };
    }
}
