package work.managerbe.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 서명과 토큰 및 쿠키 만료 정책을 외부 설정에서 읽는다.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration,
        Duration refreshTokenRetention,
        boolean secureCookie
) {
}
