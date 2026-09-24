package work.managerbe.oauthaccount.dto.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenResponseTest {

    @Test
    void Access_Token_응답을_생성한다() {
        // given / when
        AccessTokenResponse response = AccessTokenResponse.of("access-token", 900L);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
    }
}
