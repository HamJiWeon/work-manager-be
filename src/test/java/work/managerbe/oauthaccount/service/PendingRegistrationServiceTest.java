package work.managerbe.oauthaccount.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import work.managerbe.oauthaccount.domain.OAuthProvider;
import work.managerbe.oauthaccount.domain.PendingRegistration;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.repository.PendingRegistrationRepository;

import java.security.SecureRandom;
import java.time.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 만료 시각과 토큰 원문 미보관 및 임시 상태 폐기를 검증한다. */
class PendingRegistrationServiceTest {
    private final PendingRegistrationRepository repository = mock(PendingRegistrationRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);
    private final PendingRegistrationService service = new PendingRegistrationService(repository, new SecureRandom(), clock, mock(work.managerbe.oauthaccount.repository.RegistrationTermRepository.class));
    private final OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GOOGLE, "subject", "신규", "new@example.com", null);

    @Test
    void 임시_토큰_원문_대신_해시와_십분_만료를_저장한다() {
        // given / when
        String token = service.issue(info, null);
        // then
        var captor = ArgumentCaptor.forClass(PendingRegistration.class);
        verify(repository).save(captor.capture());
        assertThat(token).hasSize(43);
        assertThat(captor.getValue().getTokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(LocalDateTime.now(clock).plusMinutes(10));
        assertThat(captor.getValue().getProviderUserId()).isEqualTo(info.providerUserId());
        assertThat(captor.getValue().getEmail()).isEqualTo(info.email());
    }

    @Test
    void 재인증하면_이전_임시_토큰을_폐기한다() {
        // given / when
        service.issue(info, "previous");
        // then
        verify(repository).deleteByTokenHash(anyString());
        verify(repository).save(any());
    }

    @Test
    void 쿠키가_없거나_공백이면_인증_오류를_반환한다() {
        // given / when / then
        assertThatThrownBy(() -> service.get(null)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.get(" ")).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void 알수없는_토큰은_인증_오류를_반환한다() {
        // given / when / then
        assertThatThrownBy(() -> service.get("unknown")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void 만료_정보는_현재_시각_이하로_정리한다() {
        // given / when
        service.cleanup();
        // then
        verify(repository).deleteByExpiresAtLessThanEqual(LocalDateTime.now(clock));
    }
}
