package work.managerbe.oauthaccount.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.global.security.JwtProperties;
import work.managerbe.global.security.TokenPair;
import work.managerbe.oauthaccount.service.RefreshTokenService;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationSuccessHandlerTest {

    /**
     * OAuth 로그인 성공 시 토큰 쿠키를 발급하고 임시 세션을 제거한 뒤 프론트로 이동하는지 검증한다.
     */
    @Test
    void OAuth_로그인_성공_시_JWT를_발급한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        UserRepository userRepository = mock(UserRepository.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        AuthCookieService authCookieService = mock(AuthCookieService.class);
        JwtProperties properties = new JwtProperties("a".repeat(32), Duration.ofMinutes(15),
                Duration.ofDays(14), true, "/login/success");
        TokenPair tokenPair = new TokenPair("access", "refresh");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenService.issue(user)).thenReturn(tokenPair);
        var handler = new JwtAuthenticationSuccessHandler(
                userRepository, refreshTokenService, authCookieService, properties);
        var request = new MockHttpServletRequest();
        request.getSession();
        var response = new MockHttpServletResponse();
        var principal = new InternalOAuth2User(userId, mock(OAuth2User.class));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        // when
        handler.onAuthenticationSuccess(request, response, authentication);

        // then
        verify(authCookieService).addRefreshToken(response, tokenPair.refreshToken());
        assertThat(response.getRedirectedUrl()).isEqualTo("/login/success");
        assertThat(request.getSession(false)).isNull();
    }
}
