package work.managerbe.oauthaccount.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.managerbe.oauthaccount.domain.OAuthProvider;
import work.managerbe.oauthaccount.domain.OauthAccount;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.repository.OauthAccountRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OauthAccountCreatorTest {

    @Mock
    private OauthAccountRepository oauthAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void 사용자와_OAuth_계정을_함께_저장한다() {
        // given
        OAuthUserInfo info = new OAuthUserInfo(
                OAuthProvider.GOOGLE, "provider-id", "홍길동", "user@example.com", "image-url");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OauthAccountCreator creator = new OauthAccountCreator(oauthAccountRepository, userRepository);

        // when
        User result = creator.create(info);

        // then
        ArgumentCaptor<OauthAccount> accountCaptor = ArgumentCaptor.forClass(OauthAccount.class);
        verify(oauthAccountRepository).saveAndFlush(accountCaptor.capture());
        assertThat(result.getName()).isEqualTo("홍길동");
        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(result.getProfileImgUrl()).isEqualTo("image-url");
        assertThat(accountCaptor.getValue().getUser()).isSameAs(result);
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(accountCaptor.getValue().getProviderUserId()).isEqualTo("provider-id");
    }
}
