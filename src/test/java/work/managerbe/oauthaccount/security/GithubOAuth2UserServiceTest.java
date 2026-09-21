package work.managerbe.oauthaccount.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.service.OauthAccountService;
import work.managerbe.user.domain.User;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubOAuth2UserServiceTest {

    @Mock
    private DefaultOAuth2UserService delegate;

    @Mock
    private OauthAccountService accountService;

    @Mock
    private OAuth2UserRequest request;

    @Mock
    private ClientRegistration registration;

    @Mock
    private OAuth2User oauth2User;

    @Mock
    private User user;

    @InjectMocks
    private GithubOAuth2UserService service;

    /**
     * GitHub의 숫자 ID와 공개 프로필을 내부 계정에 연결하고 사용자 ID를 principal에 담는지 검증한다.
     */
    @Test
    void 깃허브_사용자_정보를_내부_계정에_연결한다() {
        // given
        UUID userId = UUID.randomUUID();
        when(delegate.loadUser(request)).thenReturn(oauth2User);
        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("github");
        when(oauth2User.getAttribute("id")).thenReturn(12345);
        when(oauth2User.getAttribute("login")).thenReturn("octocat");
        when(oauth2User.getAttribute("name")).thenReturn(null);
        when(oauth2User.getAttribute("email")).thenReturn(null);
        when(oauth2User.getAttribute("avatar_url")).thenReturn("avatar-url");
        when(accountService.findOrCreate(new OAuthUserInfo(
                "github", "12345", "octocat", null, "avatar-url")))
                .thenReturn(user);
        when(user.getId()).thenReturn(userId);

        // when
        OAuth2User result = service.loadUser(request);

        // then
        ArgumentCaptor<OAuthUserInfo> infoCaptor = ArgumentCaptor.forClass(OAuthUserInfo.class);
        verify(accountService).findOrCreate(infoCaptor.capture());
        assertThat(infoCaptor.getValue().providerUserId()).isEqualTo("12345");
        assertThat(infoCaptor.getValue().name()).isEqualTo("octocat");
        assertThat(result).isInstanceOf(InternalOAuth2User.class);
        assertThat(((InternalOAuth2User) result).getUserId()).isEqualTo(userId);
    }

    /**
     * GitHub ID가 없으면 계정을 저장하지 않고 인증 실패로 처리하는지 검증한다.
     */
    @Test
    void 깃허브_ID가_없으면_인증에_실패한다() {
        // given
        when(delegate.loadUser(request)).thenReturn(oauth2User);

        // when & then
        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("GitHub 사용자 ID가 없습니다.");
        verifyNoInteractions(accountService);
    }

    /**
     * GitHub ID가 공백이면 계정을 저장하지 않고 인증 실패로 처리하는지 검증한다.
     */
    @Test
    void 깃허브_ID가_공백이면_인증에_실패한다() {
        // given
        when(delegate.loadUser(request)).thenReturn(oauth2User);
        when(oauth2User.getAttribute("id")).thenReturn(" ");

        // when & then
        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("GitHub 사용자 ID가 없습니다.");
        verifyNoInteractions(accountService);
    }

    /**
     * 이름과 로그인명이 모두 없으면 계정을 저장하지 않고 인증 실패로 처리하는지 검증한다.
     */
    @Test
    void 깃허브_이름과_로그인명이_없으면_인증에_실패한다() {
        // given
        when(delegate.loadUser(request)).thenReturn(oauth2User);
        when(oauth2User.getAttribute("id")).thenReturn(12345);

        // when & then
        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("GitHub 사용자 이름이 없습니다.");
        verifyNoInteractions(accountService);
    }
}
