package work.managerbe.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import work.managerbe.global.security.JwtTokenService;

import java.util.UUID;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtEncoder jwtEncoder;

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

    @Test
    void 로그아웃은_CSRF_토큰이_없으면_403을_반환한다() throws Exception {
        // given / when & then
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRF_토큰을_헤더로_전달하면_로그아웃을_허용한다() throws Exception {
        // given
        MvcResult csrfResult = mockMvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        String csrfToken = csrfResult.getResponse().getCookie("XSRF-TOKEN").getValue();

        // when & then
        mockMvc.perform(post("/auth/logout")
                        .cookie(csrfResult.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void 토큰_갱신은_CSRF_토큰이_없으면_403을_반환한다() throws Exception {
        // given / when & then
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 토큰_갱신에_CSRF_토큰을_전달하면_인증_로직까지_진행한다() throws Exception {
        // given
        MvcResult csrfResult = mockMvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = csrfResult.getResponse().getCookie("XSRF-TOKEN").getValue();

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(csrfResult.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void Bearer_API는_CSRF_토큰_없이_POST_요청을_허용한다() throws Exception {
        // given
        String accessToken = jwtTokenService.createAccessToken(UUID.randomUUID());

        // when & then
        mockMvc.perform(post("/not-found")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
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

    @Test
    void JWT_subject가_UUID_형식이_아니면_401을_반환한다() throws Exception {
        // given
        String accessToken = createAccessToken("invalid-subject", true);

        // when & then
        mockMvc.perform(get("/not-found")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void JWT_subject가_없으면_401을_반환한다() throws Exception {
        // given
        String accessToken = createAccessToken(null, false);

        // when & then
        mockMvc.perform(get("/not-found")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    private String createAccessToken(String subject, boolean includeSubject) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("manager-be")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
        if (includeSubject) {
            claims.subject(subject);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
