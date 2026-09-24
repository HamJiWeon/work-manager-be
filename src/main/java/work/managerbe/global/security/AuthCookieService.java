package work.managerbe.global.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 인증 토큰 쿠키의 보안 속성과 생성 및 삭제 방식을 한곳에서 관리한다.
 */
@Component
public class AuthCookieService {

    public static final String REFRESH_TOKEN_COOKIE = "REFRESH_TOKEN";
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

    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
