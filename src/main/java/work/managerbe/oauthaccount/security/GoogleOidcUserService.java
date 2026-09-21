
package work.managerbe.oauthaccount.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.service.OauthAccountService;
import work.managerbe.user.domain.User;

@Service
@RequiredArgsConstructor
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final String INVALID_USER_INFO = "invalid_user_info";

    private final OidcUserService oidcUserService;
    private final OauthAccountService oauthAccountService;

    /**
     * Google OIDC의 subject를 계정 식별자로 사용해 내부 사용자를 연결한다.
     */
    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser oidcUser = oidcUserService.loadUser(request);
        OAuthUserInfo userInfo = validateUserInfo(request, oidcUser);
        User user = oauthAccountService.findOrCreate(userInfo);

        return new InternalOidcUser(user.getId(), oidcUser);
    }

    /**
     * Google OIDC 응답의 subject를 확인하고 내부 계정 정보로 변환한다.
     */
    private OAuthUserInfo validateUserInfo(OidcUserRequest request, OidcUser oidcUser) {
        String subject = oidcUser.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(INVALID_USER_INFO), "Google 사용자 ID가 없습니다.");
        }

        String name = oidcUser.getFullName();

        if (name == null || name.isBlank()) {
            name = oidcUser.getEmail();
        }

        if (name == null || name.isBlank()) {
            name = subject;
        }

        return new OAuthUserInfo(
                request.getClientRegistration().getRegistrationId(),
                subject,
                name,
                oidcUser.getEmail(),
                oidcUser.getPicture()
        );
    }
}
