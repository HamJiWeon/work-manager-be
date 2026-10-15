package work.managerbe.oauthaccount.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.oauthaccount.service.RefreshTokenService;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * OAuth 로그인을 내부 사용자 토큰 쌍으로 교환하고 임시 OAuth 세션을 제거한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String STATUS_PARAMETER = "status";
    private static final String SIGNUP_REQUIRED = "SIGNUP_REQUIRED";
    private static final String LOGIN_COMPLETE = "LOGIN_COMPLETE";

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;
    private final OAuthRedirectProperties redirectProperties;

    /** 신규 계정은 가입 필요 상태만 전달하고 기존 회원에게만 토큰을 발급한다. */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, @NonNull HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        UUID userId = extractUserId(Objects.requireNonNull(authentication.getPrincipal()));

        if (userId == null) {
            authCookieService.deleteRefreshToken(response);
            redirect(request, response, SIGNUP_REQUIRED);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("OAuth 로그인 사용자를 찾을 수 없습니다."));

        var tokenPair = refreshTokenService.issue(user);
        authCookieService.addRefreshToken(response, tokenPair.refreshToken());

        redirect(request, response, LOGIN_COMPLETE);
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, String status) throws IOException {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        response.sendRedirect(UriComponentsBuilder.fromUriString(redirectProperties.loginSuccessUrl())
                .replaceQueryParam(STATUS_PARAMETER, status)
                .build().toUriString());
    }

    private UUID extractUserId(Object principal) {
        return switch (principal) {
            case InternalOidcUser oidcUser -> oidcUser.userId();
            case InternalOAuth2User oauth2User -> oauth2User.userId();
            default -> throw new IllegalStateException("지원하지 않는 OAuth principal입니다.");
        };
    }
}
