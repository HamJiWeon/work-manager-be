package work.managerbe.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import work.managerbe.global.security.JwtProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigUnitTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void JWT_Secret이_32바이트보다_짧으면_거부한다() {
        // given
        JwtProperties properties = properties("short-secret");

        // when & then
        assertThatThrownBy(() -> config.jwtEncoder(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트 이상");
    }

    @Test
    void JWT_subject를_UUID_principal로_변환한다() {
        // given
        UUID userId = UUID.randomUUID();
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", userId.toString()));

        // when
        var authentication = config.jwtAuthenticationConverter().convert(jwt);

        // then
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(userId);
    }

    @Test
    void JWT_subject가_UUID_형식이_아니면_인증에_실패한다() {
        // given
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("sub", "invalid-subject"));

        // when & then
        assertThatThrownBy(() -> config.jwtAuthenticationConverter().convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("JWT subject가 올바른 UUID가 아닙니다.");
    }

    @Test
    void JWT_subject가_없으면_인증에_실패한다() {
        // given
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"), Map.of("iss", "manager-be"));

        // when & then
        assertThatThrownBy(() -> config.jwtAuthenticationConverter().convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("JWT subject가 올바른 UUID가 아닙니다.");
    }

    private JwtProperties properties(String secret) {
        return new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(14), Duration.ofDays(7), true, "/");
    }
}
