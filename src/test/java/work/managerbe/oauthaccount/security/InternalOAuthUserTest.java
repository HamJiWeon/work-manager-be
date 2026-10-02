package work.managerbe.oauthaccount.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalOAuthUserTest {

    @Test
    void OAuth2_사용자_정보를_delegate에_위임한다() {
        // given
        UUID userId = UUID.randomUUID();
        OAuth2User delegate = mock(OAuth2User.class);
        Map<String, Object> attributes = Map.of("id", 1);
        List<GrantedAuthority> authorities = List.of();
        when(delegate.getAttributes()).thenReturn(attributes);
        doReturn(authorities).when(delegate).getAuthorities();
        when(delegate.getName()).thenReturn("github-user");
        InternalOAuth2User user = new InternalOAuth2User(userId, delegate);

        // when & then
        assertThat(user.getUserId()).isEqualTo(userId);
        assertThat(user.getAttributes()).isSameAs(attributes);
        assertThat(user.getAuthorities()).isSameAs(authorities);
        assertThat(user.getName()).isEqualTo("github-user");
    }

    @Test
    void OIDC_사용자_정보를_delegate에_위임한다() {
        // given
        UUID userId = UUID.randomUUID();
        OidcUser delegate = mock(OidcUser.class);
        Map<String, Object> claims = Map.of("sub", "google-user");
        Map<String, Object> attributes = Map.of("email", "user@example.com");
        List<GrantedAuthority> authorities = List.of();
        OidcUserInfo userInfo = mock(OidcUserInfo.class);
        OidcIdToken idToken = mock(OidcIdToken.class);
        when(delegate.getClaims()).thenReturn(claims);
        when(delegate.getUserInfo()).thenReturn(userInfo);
        when(delegate.getIdToken()).thenReturn(idToken);
        when(delegate.getAttributes()).thenReturn(attributes);
        doReturn(authorities).when(delegate).getAuthorities();
        when(delegate.getName()).thenReturn("google-user");
        InternalOidcUser user = new InternalOidcUser(userId, delegate);

        // when & then
        assertThat(user.getUserId()).isEqualTo(userId);
        assertThat(user.getClaims()).isSameAs(claims);
        assertThat(user.getUserInfo()).isSameAs(userInfo);
        assertThat(user.getIdToken()).isSameAs(idToken);
        assertThat(user.getAttributes()).isSameAs(attributes);
        assertThat(user.getAuthorities()).isSameAs(authorities);
        assertThat(user.getName()).isEqualTo("google-user");
    }
}
