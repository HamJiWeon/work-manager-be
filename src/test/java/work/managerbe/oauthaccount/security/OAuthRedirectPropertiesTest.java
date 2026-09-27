package work.managerbe.oauthaccount.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthRedirectPropertiesTest {

    @Test
    void OAuth_로그인_성공_URL을_반환한다() {
        // given
        var properties = new OAuthRedirectProperties("https://web.example.com/oauth/callback");

        // when
        String successUrl = properties.loginSuccessUrl();

        // then
        assertThat(successUrl).isEqualTo("https://web.example.com/oauth/callback");
    }
}
