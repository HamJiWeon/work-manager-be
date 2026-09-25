package work.managerbe.oauthaccount.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.security.JwtProperties;
import work.managerbe.oauthaccount.repository.RefreshTokenRepository;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;
    private final Clock clock;

    /**
     * 만료되거나 폐기된 Refresh Token을 보존 기간이 지난 후 일괄 삭제한다.
     */
    @Scheduled(cron = "${security.jwt.refresh-token-cleanup-cron:0 0 3 * * *}", zone = "UTC")
    @Transactional
    public int cleanup() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(properties.refreshTokenRetention());
        return refreshTokenRepository.deleteExpiredOrRevokedBefore(cutoff);
    }
}
