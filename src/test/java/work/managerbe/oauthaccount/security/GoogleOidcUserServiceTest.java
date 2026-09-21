package work.managerbe.oauthaccount.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
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
class GoogleOidcUserServiceTest {

    @Mock
    private OidcUserService delegate;

    @Mock
    private OauthAccountService accountService;

    @Mock
    private OidcUserRequest request;

    @Mock
    private ClientRegistration registration;

    @Mock
    private OidcUser oidcUser;

    @Mock
    private User user;

    @InjectMocks
    private GoogleOidcUserService service;

    /**
     * Google의 subject와 프로필을 내부 계정에 연결하고 사용자 ID를 principal에 담는지 검증한다.
     */
    @Test
    void 구글_사용자_정보를_내부_계정에_연결한다() {
        // given
        UUID userId = UUID.randomUUID();
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("google");
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getFullName()).thenReturn("홍길동");
        when(oidcUser.getEmail()).thenReturn("user@example.com");
        when(oidcUser.getPicture()).thenReturn("image-url");
        when(accountService.findOrCreate(new OAuthUserInfo(
                "google", "google-subject", "홍길동", "user@example.com", "image-url")))
                .thenReturn(user);
        when(user.getId()).thenReturn(userId);

        // when
        OidcUser result = service.loadUser(request);

        // then
        ArgumentCaptor<OAuthUserInfo> infoCaptor = ArgumentCaptor.forClass(OAuthUserInfo.class);
        verify(accountService).findOrCreate(infoCaptor.capture());
        assertThat(infoCaptor.getValue().providerUserId()).isEqualTo("google-subject");
        assertThat(result).isInstanceOf(InternalOidcUser.class);
        assertThat(((InternalOidcUser) result).getUserId()).isEqualTo(userId);
        assertThat(result.getName()).isEqualTo(oidcUser.getName());
    }

    /**
     * Google subject가 없거나 공백이면 계정을 저장하지 않고 인증 실패로 처리하는지 검증한다.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void 구글_subject가_없으면_인증에_실패한다(String subject) {
        // given
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        if (subject != null) {
            when(oidcUser.getSubject()).thenReturn(subject);
        }

        // when & then
        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Google 사용자 ID가 없습니다.");
        verifyNoInteractions(accountService);
    }

    /**
     * 이름과 이메일이 없으면 유효한 subject를 이름으로 사용해 계정을 연결하는지 검증한다.
     */
    @Test
    void 이름과_이메일이_없으면_subject를_이름으로_사용한다() {
        // given
        when(delegate.loadUser(request)).thenReturn(oidcUser);
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(request.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("google");
        when(accountService.findOrCreate(new OAuthUserInfo(
                "google", "google-subject", "google-subject", null, null)))
                .thenReturn(user);

        // when
        service.loadUser(request);

        // then
        verify(accountService).findOrCreate(new OAuthUserInfo(
                "google", "google-subject", "google-subject", null, null));
    }
}
