package work.managerbe.oauthaccount.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import work.managerbe.global.security.JwtProperties;
import work.managerbe.global.security.JwtTokenService;
import work.managerbe.global.security.TokenPair;
import work.managerbe.oauthaccount.domain.RefreshToken;
import work.managerbe.oauthaccount.repository.RefreshTokenRepository;
import work.managerbe.user.domain.User;

import java.security.SecureRandom;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repository;
    private JwtTokenService jwtTokenService;
    private RefreshTokenService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        jwtTokenService = mock(JwtTokenService.class);
        JwtProperties properties = new JwtProperties("a".repeat(32), Duration.ofMinutes(15),
                Duration.ofDays(14), true, "/");
        service = new RefreshTokenService(repository, jwtTokenService, properties,
                new SecureRandom(new byte[]{1, 2, 3}), clock);
    }

    @Test
    void 토큰_쌍을_발급하고_Refresh_Token은_해시로_저장한다() {
        // given
        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(jwtTokenService.createAccessToken(userId)).thenReturn("access-token");

        // when
        TokenPair pair = service.issue(user);

        // then
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertThat(pair.accessToken()).isEqualTo("access-token");
        assertThat(pair.refreshToken()).isNotBlank();
        assertThat(captor.getValue().getTokenHash()).hasSize(64).isNotEqualTo(pair.refreshToken());
        assertThat(captor.getValue().getExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(14));
    }

    @Test
    void Refresh_Token을_회전하면_기존_토큰을_폐기한다() {
        // given
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(jwtTokenService.createAccessToken(any())).thenReturn("new-access");
        RefreshToken storedToken = RefreshToken.create(user, "stored-hash",
                LocalDateTime.now(clock).plusDays(1));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(storedToken));

        // when
        TokenPair result = service.rotate("old-refresh");

        // then
        assertThat(storedToken.getRevokedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(result.accessToken()).isEqualTo("new-access");
        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void 유효하지_않은_Refresh_Token은_거부한다() {
        // given
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.rotate("invalid"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("유효하지 않은 Refresh Token");
    }

    @Test
    void 만료된_Refresh_Token은_회전할_수_없다() {
        // given
        RefreshToken expiredToken = RefreshToken.create(mock(User.class), "hash",
                LocalDateTime.now(clock));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

        // when & then
        assertThatThrownBy(() -> service.rotate("expired"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("유효하지 않은 Refresh Token");
    }

    @Test
    void 로그아웃하면_Refresh_Token을_폐기한다() {
        // given
        RefreshToken storedToken = RefreshToken.create(mock(User.class), "hash",
                LocalDateTime.now(clock).plusDays(1));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(storedToken));

        // when
        service.revoke("refresh");

        // then
        assertThat(storedToken.getRevokedAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    void 로그아웃_토큰이_없거나_이미_만료되면_폐기를_건너뛴다() {
        // given
        RefreshToken expiredToken = RefreshToken.create(mock(User.class), "hash",
                LocalDateTime.now(clock));
        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(expiredToken));

        // when
        service.revoke("missing");
        service.revoke("expired");

        // then
        assertThat(expiredToken.getRevokedAt()).isNull();
    }
}
