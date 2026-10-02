package work.managerbe.oauthaccount.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.global.security.JwtProperties;
import work.managerbe.global.security.TokenPair;
import work.managerbe.oauthaccount.service.RefreshTokenService;

import java.time.Duration;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthCookieService.class)
class AuthControllerMvcTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtProperties jwtProperties;

    @Autowired
    AuthControllerMvcTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeEach
    void setUp() {
        when(jwtProperties.accessTokenExpiration()).thenReturn(Duration.ofMinutes(15));
        when(jwtProperties.refreshTokenExpiration()).thenReturn(Duration.ofDays(14));
        when(jwtProperties.secureCookie()).thenReturn(true);
    }

    @Test
    void Refresh_Token_쿠키를_회전하고_새_쿠키와_Access_Token을_반환한다() throws Exception {
        // given
        when(refreshTokenService.rotate("old-refresh"))
                .thenReturn(new TokenPair("new-access", "new-refresh"));

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(header().string(SET_COOKIE, allOf(
                        containsString("REFRESH_TOKEN=new-refresh"),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/"))));
        verify(refreshTokenService).rotate("old-refresh");
    }

    @Test
    void Refresh_Token_쿠키가_없으면_401을_반환한다() throws Exception {
        // given / when & then
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void 잘못된_Refresh_Token이면_401을_반환한다() throws Exception {
        // given
        when(refreshTokenService.rotate("invalid-refresh"))
                .thenThrow(new BadCredentialsException("invalid refresh token"));

        // when & then
        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "invalid-refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().doesNotExist(SET_COOKIE));
    }
}
