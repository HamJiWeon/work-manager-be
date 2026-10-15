package work.managerbe.oauthaccount.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import work.managerbe.oauthaccount.domain.OauthAccount;
import work.managerbe.oauthaccount.domain.OAuthProvider;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.repository.OauthAccountRepository;
import work.managerbe.user.domain.User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OauthAccountServiceImplTest {

    @Mock
    private OauthAccountRepository oauthAccountRepository;

    @Mock
    private OauthAccountCreator oauthAccountCreator;

    @InjectMocks
    private OauthAccountServiceImpl service;

    /** 신규 소셜 계정 조회는 회원 생성 없이 빈 결과를 반환한다. */
    @Test
    void 신규_계정_조회는_회원을_생성하지_않는다() {
        // given
        var info = new OAuthUserInfo(OAuthProvider.GOOGLE, "new-id", "신규", null, null);

        // when
        var result = service.findExisting(info);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(oauthAccountCreator);
    }

    /** 기존 소셜 계정 조회는 연결된 회원을 반환한다. */
    @Test
    void 기존_계정_조회는_연결된_회원을_반환한다() {
        // given
        var info = new OAuthUserInfo(OAuthProvider.GITHUB, "existing-id", "기존", null, null);
        var user = User.create("기존", null, null);
        var account = OauthAccount.create(user, info.provider(), info.providerUserId());
        when(oauthAccountRepository.findByProviderAndProviderUserId(info.provider(), info.providerUserId()))
                .thenReturn(Optional.of(account));

        // when
        var result = service.findExisting(info);

        // then
        assertThat(result).contains(user);
        verifyNoInteractions(oauthAccountCreator);
    }

    /**
     * 처음 로그인한 OAuth 계정의 사용자와 계정 연결 정보를 함께 저장하는지 검증한다.
     */
    @Test
    void 처음_로그인한_계정은_사용자와_OAuth_계정을_생성한다() {
        // given
        OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GOOGLE, "provider-id", "홍길동", "user@example.com", "image-url");
        User user = User.create("홍길동", "user@example.com", "image-url");
        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "provider-id"))
                .thenReturn(Optional.empty());
        when(oauthAccountCreator.create(info)).thenReturn(user);

        // when
        User result = service.findOrCreate(info);

        // then
        assertThat(result).isSameAs(user);
        verify(oauthAccountCreator).create(info);
    }

    /**
     * 연결된 OAuth 계정이 있으면 새 사용자를 저장하지 않고 기존 사용자를 반환하는지 검증한다.
     */
    @Test
    void 기존_계정은_연결된_사용자를_반환한다() {
        // given
        OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GOOGLE, "provider-id", "홍길동", null, null);
        User user = User.create("기존 사용자", null, null);
        OauthAccount account = OauthAccount.create(user, OAuthProvider.GOOGLE, "provider-id");
        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "provider-id"))
                .thenReturn(Optional.of(account));

        // when
        User result = service.findOrCreate(info);

        // then
        assertThat(result).isSameAs(user);
        verifyNoInteractions(oauthAccountCreator);
    }

    @Test
    void 동시_생성_충돌이_발생하면_기존_계정을_재조회한다() {
        // given
        OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GOOGLE, "provider-id", "홍길동", null, null);
        User existingUser = User.create("기존 사용자", null, null);
        OauthAccount account = OauthAccount.create(existingUser, OAuthProvider.GOOGLE, "provider-id");
        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "provider-id"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(account));
        when(oauthAccountCreator.create(info)).thenThrow(new DataIntegrityViolationException("unique conflict"));

        // when
        User result = service.findOrCreate(info);

        // then
        assertThat(result).isSameAs(existingUser);
        verify(oauthAccountRepository, times(2))
                .findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "provider-id");
    }

    @Test
    void 생성_실패_후_계정을_찾지_못하면_예외를_전파한다() {
        // given
        OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GOOGLE, "provider-id", "홍길동", null, null);
        DataIntegrityViolationException exception = new DataIntegrityViolationException("constraint violation");
        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "provider-id"))
                .thenReturn(Optional.empty());
        when(oauthAccountCreator.create(info)).thenThrow(exception);

        // when & then
        assertThatThrownBy(() -> service.findOrCreate(info))
                .isSameAs(exception);
    }
}
