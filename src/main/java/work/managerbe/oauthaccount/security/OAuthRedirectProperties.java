package work.managerbe.oauthaccount.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * OAuth 로그인 요청의 클라이언트별 성공 리다이렉트 허용 목록을 관리한다.
 */
@ConfigurationProperties(prefix = "security.oauth")
public record OAuthRedirectProperties(
        String defaultClient,
        Map<String, String> successUrls
) {

    public OAuthRedirectProperties {
        successUrls = Map.copyOf(successUrls);
    }

    public String successUrl(String client) {
        String successUrl = successUrls.get(client);
        if (successUrl == null || successUrl.isBlank()) {
            throw new IllegalArgumentException("지원하지 않는 OAuth 클라이언트입니다.");
        }
        return successUrl;
    }
}
