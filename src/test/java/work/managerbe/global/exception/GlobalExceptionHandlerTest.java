package work.managerbe.global.exception;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
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

    @RestController
    static class TestController {
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
