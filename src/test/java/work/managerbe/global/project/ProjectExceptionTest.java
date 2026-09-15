package work.managerbe.global.project;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로젝트 예외 생성 시 오류 코드, 상세 정보와 원인이 보존되는지 검증한다.
 */
class ProjectExceptionTest {

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