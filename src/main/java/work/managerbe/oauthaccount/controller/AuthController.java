package work.managerbe.oauthaccount.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.global.security.JwtProperties;
import work.managerbe.global.security.TokenPair;
import work.managerbe.oauthaccount.dto.response.AccessTokenResponse;
import work.managerbe.oauthaccount.dto.response.CsrfTokenResponse;
import work.managerbe.oauthaccount.service.RefreshTokenService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;
    private final JwtProperties jwtProperties;

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return CsrfTokenResponse.from(csrfToken);
    }

    @PostMapping("/refresh")
    public AccessTokenResponse refresh(
            @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh Token이 없습니다.");
        }
        TokenPair tokenPair = refreshTokenService.rotate(refreshToken);
        authCookieService.addRefreshToken(response, tokenPair.refreshToken());
        return AccessTokenResponse.of(
                tokenPair.accessToken(), jwtProperties.accessTokenExpiration().toSeconds());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
        authCookieService.deleteRefreshToken(response);
    }
}
