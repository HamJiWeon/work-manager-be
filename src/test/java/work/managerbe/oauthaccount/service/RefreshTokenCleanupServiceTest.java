package work.managerbe.oauthaccount.service;

import org.junit.jupiter.api.Test;
import work.managerbe.global.security.JwtProperties;
import work.managerbe.oauthaccount.repository.RefreshTokenRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenCleanupServiceTest {

    @Test
    void 보존_기간이_지난_Refresh_Token을_삭제한다() {
        // given
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-25T03:00:00Z"), ZoneOffset.UTC);
        JwtProperties properties = new JwtProperties(
                "a".repeat(32), Duration.ofMinutes(15), Duration.ofDays(14), Duration.ofDays(7), true, "/");
        LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(7);
        when(repository.deleteExpiredOrRevokedBefore(cutoff)).thenReturn(3);
        RefreshTokenCleanupService service = new RefreshTokenCleanupService(repository, properties, clock);

        // when
        int deletedCount = service.cleanup();

        // then
        assertThat(deletedCount).isEqualTo(3);
        verify(repository).deleteExpiredOrRevokedBefore(cutoff);
    }
}
