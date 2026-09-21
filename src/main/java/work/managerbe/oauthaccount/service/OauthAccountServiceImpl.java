package work.managerbe.oauthaccount.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.oauthaccount.domain.OauthAccount;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.repository.OauthAccountRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class OauthAccountServiceImpl implements OauthAccountService {

    private final OauthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;

    @Override
    public User findOrCreate(OAuthUserInfo userInfo) {
        return oauthAccountRepository
                .findByProviderAndProviderUserId(
                        userInfo.provider(),
                        userInfo.providerUserId())
                .map(OauthAccount::getUser)
                .orElseGet(() -> createUserWithOauthAccount(userInfo));
    }

    /**
     * OAuth 제공자 정보를 사용자로 저장한 뒤 해당 사용자와 OAuth 계정을 연결한다.
     */
    private User createUserWithOauthAccount(OAuthUserInfo userInfo) {
        User user = userRepository.save(User.create(
                userInfo.name(),
                userInfo.email(),
                userInfo.profileImageUrl()
        ));

        oauthAccountRepository.save(OauthAccount.create(
                user,
                userInfo.provider(),
                userInfo.providerUserId()
        ));

        return user;
    }
}
