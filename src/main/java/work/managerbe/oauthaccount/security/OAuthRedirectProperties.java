package work.managerbe.oauthaccount.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth 로그인 성공 후 이동할 웹 콜백 URL을 관리한다.
 */
@ConfigurationProperties(prefix = "security.oauth")
public record OAuthRedirectProperties(
        String loginSuccessUrl
) {
}
