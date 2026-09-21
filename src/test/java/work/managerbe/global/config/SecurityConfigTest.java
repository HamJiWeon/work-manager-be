package work.managerbe.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 인증되지 않은 API 요청을 보안 필터가 거부하는지 검증한다.
     */
    @Test
    void 인증되지_않은_API_요청은_거부한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when & then
        mockMvc.perform(get("/{userId}/{code}", userId, "WORK"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 세션 인증 상태의 쓰기 요청에도 CSRF 토큰을 요구하는지 검증한다.
     */
    @Test
    void 인증된_쓰기_요청에도_CSRF_토큰이_필요하다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        // when & then
        mockMvc.perform(post("/{userId}/projects", userId).with(user("tester")))
                .andExpect(status().isForbidden());
    }

    /**
     * 세션에 저장한 인증 상태를 다음 요청에서 읽고 로그아웃 시 세션을 폐기하는지 검증한다.
     */
    @Test
    void 세션_인증은_다음_요청까지_유지되고_로그아웃하면_폐기된다() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        var authentication = new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, java.util.List.of());
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));

        // when & then
        mockMvc.perform(get("/not-found").session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();
    }
}
