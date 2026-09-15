package work.managerbe.global.exception;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import work.managerbe.global.project.ProjectErrorCode;
import work.managerbe.global.project.ProjectException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 MVC 예외 처리에서 상태, 헤더, 응답 형식과 내부 정보 비노출을 검증한다.
 */
@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class GlobalExceptionHandlerTest {
    private final MockMvc mockMvc;

    @Test
    void 도메인_예외는_상세정보를_반환하고_원인은_노출하지_않는다() throws Exception {
        // given
        String path = "/exception-test/domain";
        // when / then
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.details.projectId").value(1))
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void 예상하지_못한_예외는_내부_메시지를_숨긴다() throws Exception {
        // given
        String path = "/exception-test/unexpected";
        // when / then
        mockMvc.perform(get(path))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void 잘못된_JSON은_공통_400_응답으로_변환한다() throws Exception {
        // given
        String body = "{";
        // when / then
        mockMvc.perform(post("/exception-test/body").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 지원하지_않는_메서드의_상태와_허용_헤더를_유지한다() throws Exception {
        // given
        String path = "/exception-test/domain";
        // when / then
        mockMvc.perform(post(path))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @CsvSource({
            "authentication, 401, UNAUTHORIZED, 인증이 필요합니다.",
            "access-denied, 403, FORBIDDEN, 접근 권한이 없습니다.",
            "server-error, 500, INTERNAL_SERVER_ERROR, 서버 내부 오류가 발생했습니다."
    })
    void 인증_인가_및_공통_서버_예외를_공개_응답으로_변환한다(
            String endpoint, int expectedStatus, String expectedCode, String expectedMessage) throws Exception {
        // given
        String path = "/exception-test/" + endpoint;

        // when / then
        mockMvc.perform(get(path))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.cause").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "401, UNAUTHORIZED",
            "403, FORBIDDEN",
            "404, RESOURCE_NOT_FOUND",
            "409, CONFLICT",
            "400, INVALID_REQUEST",
            "499, INVALID_REQUEST",
            "500, INTERNAL_SERVER_ERROR",
            "503, INTERNAL_SERVER_ERROR"
    })
    void MVC_예외의_상태를_유지하고_공통_오류_코드로_변환한다(
            int httpStatus, String expectedCode) throws Exception {
        // given
        String path = "/exception-test/status";

        // when / then
        mockMvc.perform(get(path).param("status", Integer.toString(httpStatus)))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.message").value(
                        ErrorCode.valueOf(expectedCode).getMessage()))
                .andExpect(jsonPath("$.cause").doesNotExist());
    }

    @Test
    void 원인_없는_도메인_예외도_공개_응답으로_변환한다() throws Exception {
        // given
        String path = "/exception-test/domain-without-cause";

        // when / then
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("프로젝트를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @RestController
    static class TestController {
        @GetMapping("/exception-test/authentication")
        public void authentication() {
            throw new BadCredentialsException("내부 인증 정보");
        }

        @GetMapping("/exception-test/access-denied")
        public void accessDenied() {
            throw new AccessDeniedException("내부 권한 정보");
        }

        @GetMapping("/exception-test/server-error")
        public void serverError() {
            throw CommonException.of(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        @GetMapping("/exception-test/status")
        public void status(@RequestParam("status") int status) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(status), "내부 오류 정보");
        }

        @GetMapping("/exception-test/domain-without-cause")
        public void domainWithoutCause() {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        @GetMapping("/exception-test/domain")
        public void domain() {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND,
                    Map.of("projectId", 1), new IllegalStateException("internal cause"));
        }

        @GetMapping("/exception-test/unexpected")
        public void unexpected() {
            throw new IllegalStateException("internal database information");
        }

        @PostMapping("/exception-test/body")
        public void body(@RequestBody Map<String, Object> body) {
        }
    }
}
