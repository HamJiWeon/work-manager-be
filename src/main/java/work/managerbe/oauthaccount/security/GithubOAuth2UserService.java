package work.managerbe.oauthaccount.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.service.OauthAccountService;
import work.managerbe.user.domain.User;

@Service
@RequiredArgsConstructor
public class GithubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String INVALID_USER_INFO = "invalid_user_info";

    private final DefaultOAuth2UserService defaultOAuth2UserService;
    private final OauthAccountService oauthAccountService;

    /**
     * GitHub의 id와 공개 프로필을 내부 사용자 정보로 변환해 계정을 연결한다.
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = defaultOAuth2UserService.loadUser(request);
        OAuthUserInfo userInfo = validateUserInfo(request, oauth2User);
        User user = oauthAccountService.findOrCreate(userInfo);

        return new InternalOAuth2User(user.getId(), oauth2User);
    }

    /**
     * GitHub 응답의 필수 식별자와 이름을 확인하고 내부 계정 정보로 변환한다.
     */
    private OAuthUserInfo validateUserInfo(OAuth2UserRequest request, OAuth2User oauth2User) {
        Object providerUserId = oauth2User.getAttribute("id");
        if (providerUserId == null || providerUserId.toString().isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(INVALID_USER_INFO), "GitHub 사용자 ID가 없습니다.");
        }

        String login = oauth2User.getAttribute("login");
        String name = oauth2User.getAttribute("name");

        if (name == null || name.isBlank()) {
            name = login;
        }
        if (name == null || name.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(INVALID_USER_INFO), "GitHub 사용자 이름이 없습니다.");
        }

        return new OAuthUserInfo(
                request.getClientRegistration().getRegistrationId(),
                providerUserId.toString(),
                name,
                oauth2User.getAttribute("email"),
                oauth2User.getAttribute("avatar_url")
        );
    }
}
