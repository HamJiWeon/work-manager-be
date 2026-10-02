package work.managerbe.oauthaccount.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.global.security.JwtProperties;
import work.managerbe.global.security.TokenPair;
import work.managerbe.oauthaccount.dto.response.AccessTokenResponse;
import work.managerbe.oauthaccount.service.RefreshTokenService;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private RefreshTokenService refreshTokenService;
    private AuthCookieService authCookieService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        refreshTokenService = mock(RefreshTokenService.class);
        authCookieService = mock(AuthCookieService.class);
        JwtProperties properties = new JwtProperties(
                "a".repeat(32), Duration.ofMinutes(15), Duration.ofDays(14), Duration.ofDays(7), true);
        controller = new AuthController(refreshTokenService, authCookieService, properties);
    }

    @Test
    void Refresh_Token을_회전하고_Access_Token을_반환한다() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        TokenPair tokenPair = new TokenPair("new-access", "new-refresh");
        when(refreshTokenService.rotate("refresh")).thenReturn(tokenPair);

        // when
        AccessTokenResponse result = controller.refresh("refresh", response);

        // then
        assertThat(result).isEqualTo(AccessTokenResponse.of("new-access", 900L));
        verify(authCookieService).addRefreshToken(response, "new-refresh");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void Refresh_Token이_없으면_재발급을_거부한다(String refreshToken) {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when & then
        assertThatThrownBy(() -> controller.refresh(refreshToken, response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Refresh Token이 없습니다");
        verifyNoInteractions(refreshTokenService, authCookieService);
    }

    @Test
    void 로그아웃하면_Refresh_Token을_폐기하고_쿠키를_삭제한다() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        controller.logout("refresh", response);

        // then
        verify(refreshTokenService).revoke("refresh");
        verify(authCookieService).deleteRefreshToken(response);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void Refresh_Token이_없어도_로그아웃_쿠키는_삭제한다(String refreshToken) {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        controller.logout(refreshToken, response);

        // then
        verifyNoInteractions(refreshTokenService);
        verify(authCookieService).deleteRefreshToken(response);
    }
}
