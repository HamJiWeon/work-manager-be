package work.managerbe.oauthaccount.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;

class OauthAccountTest {

    @Test
    @DisplayName("OAuth 계정을 생성하면 사용자와 제공자 정보가 저장된다.")
    void OAuth_계정_생성() {
        // given
        User user = User.create("홍길동", "user@example.com", null);
        String provider = "google";
        String providerUserId = "google-user-123";

        // when
        OauthAccount oauthAccount = OauthAccount.create(user, provider, providerUserId);

        // then
        assertThat(oauthAccount.getUser()).isSameAs(user);
        assertThat(oauthAccount.getProvider()).isEqualTo(provider);
        assertThat(oauthAccount.getProviderUserId()).isEqualTo(providerUserId);
        assertThat(oauthAccount.getId()).isNull();
        assertThat(oauthAccount.getCreatedAt()).isNull();
    }
}