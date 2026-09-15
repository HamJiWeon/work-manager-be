package work.managerbe.global.card;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 오류 코드 계약과 예외 팩터리의 기본값, 원인 보존 및 상세 정보의 방어적 복사를 검증한다.
 */
class CardExceptionTest {

    private static final CardErrorCode ERROR_CODE = CardErrorCode.CARD_NOT_FOUND;

    @Test
    void 오류_코드의_식별자와_HTTP_상태와_메시지를_제공한다() {
        // given / when / then
        assertThat(ERROR_CODE.name()).isEqualTo("CARD_NOT_FOUND");
        assertThat(ERROR_CODE.getName()).isEqualTo("CRD-001");
        assertThat(ERROR_CODE.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ERROR_CODE.getMessage()).isEqualTo("카드를 찾을 수 없습니다.");
    }

    @Test
    void 오류_코드만으로_생성하면_상세정보는_비어있고_원인은_없다() {
        // given / when
        CardException exception = CardException.of(ERROR_CODE);

        // then
        assertThat(exception.getErrorCode()).isSameAs(ERROR_CODE);
        assertThat(exception.getMessage()).isEqualTo("카드를 찾을 수 없습니다.");
        assertThat(exception.getDetails()).isEmpty();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void 상세정보를_복사하고_외부_변경으로부터_보호한다() {
        // given
        Map<String, Object> details = new HashMap<>(Map.of("field", "id"));

        // when
        CardException exception = CardException.of(ERROR_CODE, details);
        details.put("field", "changed");

        // then
        assertThat(exception.getErrorCode()).isSameAs(ERROR_CODE);
        assertThat(exception.getDetails()).containsExactlyEntriesOf(Map.of("field", "id"));
        assertThat(exception.getCause()).isNull();
        assertThatThrownBy(() -> exception.getDetails().put("field", "changed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 상세정보와_원인_예외를_보존한다() {
        // given
        Map<String, Object> details = Map.of("field", "id");
        Throwable cause = new IllegalStateException("내부 오류");

        // when
        CardException exception = CardException.of(ERROR_CODE, details, cause);

        // then
        assertThat(exception.getErrorCode()).isSameAs(ERROR_CODE);
        assertThat(exception.getMessage()).isEqualTo("카드를 찾을 수 없습니다.");
        assertThat(exception.getDetails()).containsExactlyEntriesOf(details);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void null_상세정보는_빈_맵으로_변환한다() {
        // given / when
        CardException exception = CardException.of(ERROR_CODE, null);

        // then
        assertThat(exception.getDetails()).isEmpty();
    }

    @Test
    void 오류_코드가_null이면_거부한다() {
        // given / when / then
        assertThatThrownBy(() -> CardException.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("errorCode는 필수입니다.");
    }
}
