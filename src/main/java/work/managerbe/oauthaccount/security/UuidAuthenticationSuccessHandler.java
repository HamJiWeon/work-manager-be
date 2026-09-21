package work.managerbe.oauthaccount.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;
import java.util.UUID;

/**
 * OAuth 로그인 결과를 내부 사용자 UUID를 principal로 갖는 세션 인증으로 저장한다.
 */
public class UuidAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final SecurityContextRepository securityContextRepository;

    public UuidAuthenticationSuccessHandler(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * OAuth principal의 내부 ID로 인증 객체를 만들고 세션에 저장한 뒤 로그인 후 이동을 처리한다.
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        UUID userId = switch (authentication.getPrincipal()) {
            case InternalOidcUser oidcUser -> oidcUser.userId();
            case InternalOAuth2User oauth2User -> oauth2User.userId();
            default -> throw new IllegalStateException("지원하지 않는 OAuth principal입니다.");
        };
        Authentication internalAuthentication = new UsernamePasswordAuthenticationToken(
                userId, null, authentication.getAuthorities());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(internalAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        super.onAuthenticationSuccess(request, response, internalAuthentication);
    }
}
