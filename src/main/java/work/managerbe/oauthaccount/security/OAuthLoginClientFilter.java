package work.managerbe.oauthaccount.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * OAuth 로그인 시작 요청의 클라이언트를 허용 목록으로 검증하고 임시 세션에 보관한다.
 */
public class OAuthLoginClientFilter extends OncePerRequestFilter {

    public static final String OAUTH_CLIENT_SESSION_ATTRIBUTE = "oauth_login_client";

    private final OAuthRedirectProperties properties;

    public OAuthLoginClientFilter(OAuthRedirectProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI + "/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String client = request.getParameter("client");
        if (client == null || client.isBlank()) {
            client = properties.defaultClient();
        }

        try {
            properties.successUrl(client);
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
            return;
        }

        request.getSession(true).setAttribute(OAUTH_CLIENT_SESSION_ATTRIBUTE, client);
        filterChain.doFilter(request, response);
    }
}
