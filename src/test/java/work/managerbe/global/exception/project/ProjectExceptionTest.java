package work.managerbe.global.exception.project;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 오류 코드 계약과 예외 팩터리의 기본값, 원인 보존 및 상세 정보의 방어적 복사를 검증한다.
 */
class ProjectExceptionTest {

    private static final ProjectErrorCode ERROR_CODE = ProjectErrorCode.PROJECT_NOT_FOUND;

    @Test
    void 오류_코드의_식별자와_HTTP_상태와_메시지를_제공한다() {
        // given / when / then
        assertThat(ERROR_CODE.name()).isEqualTo("PROJECT_NOT_FOUND");
        assertThat(ERROR_CODE.getName()).isEqualTo("PJT-001");
        assertThat(ERROR_CODE.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ERROR_CODE.getMessage()).isEqualTo("프로젝트를 찾을 수 없습니다.");
    }

    @Test
    void 접두사_중복_오류는_CONFLICT_상태를_제공한다() {
        // given
        ProjectErrorCode errorCode = ProjectErrorCode.PROJECT_DUPLICATE_PREFIX;

        // when
        ProjectException exception = ProjectException.of(errorCode);

        // then
        assertThat(exception.getErrorCode()).isSameAs(errorCode);
        assertThat(errorCode.getName()).isEqualTo("PJT-003");
        assertThat(errorCode.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 오류_코드만으로_생성하면_상세정보는_비어있고_원인은_없다() {
        // given / when
        ProjectException exception = ProjectException.of(ERROR_CODE);

        // then
        assertThat(exception.getErrorCode()).isSameAs(ERROR_CODE);
        assertThat(exception.getMessage()).isEqualTo("프로젝트를 찾을 수 없습니다.");
        assertThat(exception.getDetails()).isEmpty();
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void 상세정보를_복사하고_외부_변경으로부터_보호한다() {
        // given
        Map<String, Object> details = new HashMap<>(Map.of("field", "id"));

        // when
        ProjectException exception = ProjectException.of(ERROR_CODE, details);
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
        ProjectException exception = ProjectException.of(ERROR_CODE, details, cause);

        // then
        assertThat(exception.getErrorCode()).isSameAs(ERROR_CODE);
        assertThat(exception.getMessage()).isEqualTo("프로젝트를 찾을 수 없습니다.");
        assertThat(exception.getDetails()).containsExactlyEntriesOf(details);
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void null_상세정보는_빈_맵으로_변환한다() {
        // given / when
        ProjectException exception = ProjectException.of(ERROR_CODE, null);

        // then
        assertThat(exception.getDetails()).isEmpty();
    }

    @Test
    void 오류_코드가_null이면_거부한다() {
        // given / when / then
        assertThatThrownBy(() -> ProjectException.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("errorCode는 필수입니다.");
    }

    @Nested
    @DisplayName("프로젝트 예외 생성")
    class CreateProjectException {

        @Test
        @DisplayName("오류 코드만 전달하면 상세 정보는 비어 있고 원인은 없다.")
        void 오류_코드로_예외_생성() {
            // given
            ProjectErrorCode errorCode = ProjectErrorCode.PROJECT_NOT_FOUND;

            // when
            ProjectException exception = ProjectException.of(errorCode);

            // then
            assertThat(exception.getErrorCode()).isSameAs(errorCode);
            assertThat(exception.getMessage()).isEqualTo(errorCode.getMessage());
            assertThat(exception.getDetails()).isEmpty();
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("상세 정보를 전달하면 예외에 보존된다.")
        void 상세_정보를_포함한_예외_생성() {
            // given
            ProjectErrorCode errorCode = ProjectErrorCode.PROJECT_NOT_FOUND;
            Map<String, Object> details = Map.of("projectId", 1L);

            // when
            ProjectException exception = ProjectException.of(errorCode, details);

            // then
            assertThat(exception.getErrorCode()).isSameAs(errorCode);
            assertThat(exception.getMessage()).isEqualTo(errorCode.getMessage());
            assertThat(exception.getDetails()).isEqualTo(details);
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("상세 정보와 원인을 전달하면 예외에 함께 보존된다.")
        void 원인을_포함한_예외_생성() {
            // given
            ProjectErrorCode errorCode = ProjectErrorCode.PROJECT_NOT_FOUND;
            Map<String, Object> details = Map.of("projectId", 1L);
            Throwable cause = new IllegalStateException("조회 실패");

            // when
            ProjectException exception = ProjectException.of(errorCode, details, cause);

            // then
            assertThat(exception.getErrorCode()).isSameAs(errorCode);
            assertThat(exception.getMessage()).isEqualTo(errorCode.getMessage());
            assertThat(exception.getDetails()).isEqualTo(details);
            assertThat(exception.getCause()).isSameAs(cause);
        }
    }
}
