package work.managerbe.global.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import work.managerbe.oauthaccount.service.PendingRegistrationService;

import java.time.Duration;

/**
 * 인증 토큰 쿠키의 보안 속성과 생성 및 삭제 방식을 한곳에서 관리한다.
 */
@Component
public class AuthCookieService {

    public static final String REFRESH_TOKEN_COOKIE = "REFRESH_TOKEN";
    public static final String PENDING_REGISTRATION_COOKIE = "PENDING_REGISTRATION";
    private static final Duration DELETE_IMMEDIATELY = Duration.ZERO;

    private final JwtProperties properties;

    public AuthCookieService(JwtProperties properties) {
        this.properties = properties;
    }

    public void addRefreshToken(HttpServletResponse response, String refreshToken) {
        addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken, properties.refreshTokenExpiration());
    }

    public void deleteRefreshToken(HttpServletResponse response) {
        addCookie(response, REFRESH_TOKEN_COOKIE, "", DELETE_IMMEDIATELY);
    }

    /** 가입 전용 쿠키를 로그인 토큰과 분리해 HttpOnly로 발급한다. */
    public void addPendingRegistration(HttpServletResponse response, String token) {
        addCookie(response, PENDING_REGISTRATION_COOKIE, token,
                PendingRegistrationService.EXPIRATION, "Lax");
    }

    /** 임시 가입 쿠키를 동일한 보안 속성과 경로로 삭제한다. */
    public void deletePendingRegistration(HttpServletResponse response) {
        addCookie(response, PENDING_REGISTRATION_COOKIE, "", DELETE_IMMEDIATELY, "Lax");
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        addCookie(response, name, value, maxAge, "Strict");
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge, String sameSite) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
