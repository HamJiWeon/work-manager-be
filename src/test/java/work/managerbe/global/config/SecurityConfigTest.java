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
import work.managerbe.global.security.JwtTokenService;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

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
     * 로그인하지 않은 사용자의 현재 사용자 조회를 보안 필터가 거부하는지 검증한다.
     */
    @Test
    void 인증되지_않은_users_me_요청은_401을_반환한다() throws Exception {
        // given / when & then
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Authorization 헤더 방식에서는 CSRF 토큰 없이 쓰기 요청을 허용하는지 검증한다.
     */
    @Test
    void 로그아웃은_CSRF_토큰을_요구하지_않는다() throws Exception {
        // given / when & then
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

    /**
     * 세션에 저장한 인증 상태를 다음 요청에서 읽고 로그아웃 시 세션을 폐기하는지 검증한다.
     */
    @Test
    void 세션_인증은_사용하지_않고_JWT로_인증한다() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        var authentication = new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, java.util.List.of());
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));

        String accessToken = jwtTokenService.createAccessToken(UUID.randomUUID());

        // when & then
        mockMvc.perform(get("/not-found").session(session))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/not-found")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }
}
