package work.managerbe.global.config;

import org.junit.jupiter.api.Test;
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

    private JwtProperties properties(String secret) {
        return new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(14), Duration.ofDays(7), true, "/");
    }
}
