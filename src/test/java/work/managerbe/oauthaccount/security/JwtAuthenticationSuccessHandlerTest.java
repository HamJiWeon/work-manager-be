package work.managerbe.oauthaccount.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.global.security.TokenPair;
import work.managerbe.oauthaccount.service.RefreshTokenService;
import work.managerbe.oauthaccount.service.PendingRegistrationService;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.domain.OAuthProvider;
import jakarta.servlet.http.Cookie;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JwtAuthenticationSuccessHandlerTest {

    private final PendingRegistrationService pendingRegistrationService = mock(PendingRegistrationService.class);
    private final OAuthUserInfo userInfo = new OAuthUserInfo(OAuthProvider.GOOGLE, "social-id", "신규", null, null);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuthCookieService authCookieService = mock(AuthCookieService.class);
    private final OAuthRedirectProperties redirectProperties = new OAuthRedirectProperties("/login/success");
    private final JwtAuthenticationSuccessHandler handler = new JwtAuthenticationSuccessHandler(
            pendingRegistrationService, userRepository, refreshTokenService, authCookieService, redirectProperties);

    /**
     * OAuth 로그인 성공 시 토큰 쿠키를 발급하고 임시 세션을 제거한 뒤 프론트로 이동하는지 검증한다.
     */
    @Test
    void OAuth_로그인_성공_시_JWT를_발급한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        TokenPair tokenPair = new TokenPair("access", "refresh");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenService.issue(user)).thenReturn(tokenPair);
        var request = new MockHttpServletRequest();
        request.getSession();
        request.setCookies(new Cookie(AuthCookieService.PENDING_REGISTRATION_COOKIE, "old-pending"));
        var response = new MockHttpServletResponse();
        var principal = new InternalOAuth2User(userId, mock(OAuth2User.class));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        // when
        handler.onAuthenticationSuccess(request, response, authentication);

        // then
        verify(authCookieService).addRefreshToken(response, tokenPair.refreshToken());
        verify(pendingRegistrationService).revoke("old-pending");
        verify(authCookieService).deletePendingRegistration(response);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login/success?status=LOGIN_COMPLETE");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void OIDC_로그인도_토큰을_발급하고_세션이_없으면_그대로_진행한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        TokenPair tokenPair = new TokenPair("access", "refresh");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenService.issue(user)).thenReturn(tokenPair);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var principal = new InternalOidcUser(userId, mock(OidcUser.class));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        // when
        handler.onAuthenticationSuccess(request, response, authentication);

        // then
        verify(authCookieService).addRefreshToken(response, "refresh");
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login/success?status=LOGIN_COMPLETE");
    }

    /** 신규 소셜 계정은 토큰 발급과 회원 조회 없이 가입 필요 상태로 이동한다. */
    @Test
    void 신규_계정은_가입_필요_상태를_전달한다() throws Exception {
        // given
        var request = new MockHttpServletRequest();
        request.getSession();
        var response = new MockHttpServletResponse();
        request.setCookies(new Cookie(AuthCookieService.PENDING_REGISTRATION_COOKIE, "old-pending"));
        when(pendingRegistrationService.issue(userInfo, "old-pending")).thenReturn("new-pending");
        var principal = new InternalOAuth2User(null, mock(OAuth2User.class), userInfo);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        // when
        handler.onAuthenticationSuccess(request, response, authentication);

        // then
        assertThat(response.getRedirectedUrl()).isEqualTo("/login/success?status=SIGNUP_REQUIRED");
        assertThat(request.getSession(false)).isNull();
        verify(authCookieService).deleteRefreshToken(response);
        verify(authCookieService).addPendingRegistration(response, "new-pending");
        verify(pendingRegistrationService).issue(userInfo, "old-pending");
        verifyNoInteractions(userRepository, refreshTokenService);
    }

    /** OIDC 신규 계정도 가입 필요로 분기하며 기존 쿼리와 fragment를 보존한다. */
    @Test
    void 신규_OIDC_계정은_기존_status를_가입_필요로_교체한다() throws Exception {
        // given
        var customHandler = new JwtAuthenticationSuccessHandler(pendingRegistrationService, userRepository, refreshTokenService,
                authCookieService, new OAuthRedirectProperties("/callback?from=web&status=old#result"));
        var response = new MockHttpServletResponse();
        var authentication = new UsernamePasswordAuthenticationToken(
                new InternalOidcUser(null, mock(OidcUser.class), userInfo), null, List.of());

        // when
        customHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        // then
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/callback?from=web&status=SIGNUP_REQUIRED#result");
        verifyNoInteractions(userRepository, refreshTokenService);
        verify(authCookieService).deleteRefreshToken(response);
        verify(pendingRegistrationService).issue(userInfo, null);
    }

    @Test
    void 내부_사용자를_찾을_수_없으면_로그인에_실패한다() {
        // given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        var authentication = new UsernamePasswordAuthenticationToken(
                new InternalOAuth2User(userId, mock(OAuth2User.class)), null, List.of());

        // when & then
        assertThatThrownBy(() -> handler.onAuthenticationSuccess(
                new MockHttpServletRequest(), new MockHttpServletResponse(), authentication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    void 지원하지_않는_principal은_로그인에_실패한다() {
        // given
        var authentication = new UsernamePasswordAuthenticationToken("unsupported", null, List.of());

        // when & then
        assertThatThrownBy(() -> handler.onAuthenticationSuccess(
                new MockHttpServletRequest(), new MockHttpServletResponse(), authentication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지원하지 않는 OAuth principal");
    }

    @Test
    void principal이_null이면_로그인에_실패한다() {
        // given
        Authentication authentication = mock(Authentication.class);

        // when & then
        assertThatThrownBy(() -> handler.onAuthenticationSuccess(
                new MockHttpServletRequest(), new MockHttpServletResponse(), authentication))
                .isInstanceOf(NullPointerException.class);
    }
}
