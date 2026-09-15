package work.managerbe.global.exception;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 오류 응답의 상세 정보 기본값과 방어적 복사 및 불변성을 검증한다.
 */
class ErrorResponseTest {

    @Test
    void 상세_정보가_null이면_빈_맵으로_응답한다() {
        // given
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;

        // when
        ErrorResponse response = ErrorResponse.of(errorCode, null);

        // then
        assertThat(response.code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.message()).isEqualTo("잘못된 요청입니다.");
        assertThat(response.details()).isEmpty();
    }

    @Test
    void 상세_정보를_응답에_포함한다() {
        // given
        Map<String, Object> details = Map.of("field", "name");

        // when
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_REQUEST, details);

        // then
        assertThat(response.details()).containsExactlyEntriesOf(details);
    }

    @Test
    void 원본_맵을_변경해도_생성한_응답은_유지한다() {
        // given
        Map<String, Object> details = new HashMap<>();
        details.put("field", "name");
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_REQUEST, details);

        // when
        details.put("field", "email");
        details.put("reason", "required");

        // then
        assertThat(response.details()).containsExactlyEntriesOf(Map.of("field", "name"));
    }

    @Test
    void 응답의_상세_정보는_직접_수정할_수_없다() {
        // given
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.INVALID_REQUEST, Map.of("field", "name"));

        // when / then
        assertThatThrownBy(() -> response.details().put("field", "email"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(response.details()).containsEntry("field", "name");
    }
}
