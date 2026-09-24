package work.managerbe.oauthaccount.domain;

import org.junit.jupiter.api.Test;
import work.managerbe.user.domain.User;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    @Test
    void 만료되지_않고_폐기되지_않은_토큰만_사용할_수_있다() {
        // given
        LocalDateTime now = LocalDateTime.parse("2026-09-24T12:00:00");
        RefreshToken token = RefreshToken.create(
                User.create("사용자", null, null), "hash", now.plusDays(1));

        // when & then
        assertThat(token.isUsableAt(now)).isTrue();
        token.revoke(now);
        assertThat(token.isUsableAt(now)).isFalse();
        assertThat(token.getRevokedAt()).isEqualTo(now);
    }

    @Test
    void 만료된_토큰은_사용할_수_없다() {
        // given
        LocalDateTime now = LocalDateTime.parse("2026-09-24T12:00:00");
        RefreshToken token = RefreshToken.create(
                User.create("사용자", null, null), "hash", now.minusSeconds(1));

        // when & then
        assertThat(token.isUsableAt(now)).isFalse();
    }
}
