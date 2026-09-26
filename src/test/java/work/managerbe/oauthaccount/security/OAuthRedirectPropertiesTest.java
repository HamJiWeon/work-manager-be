package work.managerbe.oauthaccount.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthRedirectPropertiesTest {

    @Test
    void 등록된_클라이언트의_성공_URL을_반환한다() {
        // given
        var properties = new OAuthRedirectProperties(
                "web", Map.of("web", "https://web.example.com/oauth/callback"));

        // when
        String successUrl = properties.successUrl("web");

        // then
        assertThat(successUrl).isEqualTo("https://web.example.com/oauth/callback");
    }

    @Test
    void 등록되지_않은_클라이언트는_거부한다() {
        // given
        var properties = new OAuthRedirectProperties(
                "web", Map.of("web", "https://web.example.com/oauth/callback"));

        // when & then
        assertThatThrownBy(() -> properties.successUrl("mobile"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 OAuth 클라이언트");
    }
}
