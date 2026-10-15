package work.managerbe.oauthaccount.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** 기존 회원 ID와 소셜 인증 정보를 전달하며 신규 계정의 회원 ID는 null이다. */
public record InternalOAuth2User(UUID userId, OAuth2User delegate) implements OAuth2User {

    public UUID getUserId() {
        return userId;
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
