package work.managerbe.oauthaccount.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** 기존 회원 ID와 소셜 인증 정보를 전달하며 신규 계정의 회원 ID는 null이다. */
public record InternalOidcUser(UUID userId, OidcUser delegate, OAuthUserInfo userInfo) implements OidcUser {

    public InternalOidcUser(UUID userId, OidcUser delegate) {
        this(userId, delegate, null);
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
