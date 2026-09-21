package work.managerbe.oauthaccount.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.managerbe.oauthaccount.domain.OauthAccount;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.repository.OauthAccountRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OauthAccountServiceImplTest {

    @Mock
    private OauthAccountRepository oauthAccountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OauthAccountServiceImpl service;

    /**
     * 처음 로그인한 OAuth 계정의 사용자와 계정 연결 정보를 함께 저장하는지 검증한다.
     */
    @Test
    void 처음_로그인한_계정은_사용자와_OAuth_계정을_생성한다() {
        // given
        OAuthUserInfo info = new OAuthUserInfo("google", "provider-id", "홍길동", "user@example.com", "image-url");
        when(oauthAccountRepository.findByProviderAndProviderUserId("google", "provider-id"))
                .thenReturn(Optional.empty());
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        User result = service.findOrCreate(info);

        // then
        ArgumentCaptor<OauthAccount> accountCaptor = ArgumentCaptor.forClass(OauthAccount.class);
        verify(oauthAccountRepository).save(accountCaptor.capture());
        assertThat(result.getName()).isEqualTo("홍길동");
        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(result.getProfileImgUrl()).isEqualTo("image-url");
        assertThat(accountCaptor.getValue().getUser()).isSameAs(result);
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo("google");
        assertThat(accountCaptor.getValue().getProviderUserId()).isEqualTo("provider-id");
    }

    /**
     * 연결된 OAuth 계정이 있으면 새 사용자를 저장하지 않고 기존 사용자를 반환하는지 검증한다.
     */
    @Test
    void 기존_계정은_연결된_사용자를_반환한다() {
        // given
        OAuthUserInfo info = new OAuthUserInfo("google", "provider-id", "홍길동", null, null);
        User user = User.create("기존 사용자", null, null);
        OauthAccount account = OauthAccount.create(user, "google", "provider-id");
        when(oauthAccountRepository.findByProviderAndProviderUserId("google", "provider-id"))
                .thenReturn(Optional.of(account));

        // when
        User result = service.findOrCreate(info);

        // then
        assertThat(result).isSameAs(user);
        verifyNoInteractions(userRepository);
    }
}
