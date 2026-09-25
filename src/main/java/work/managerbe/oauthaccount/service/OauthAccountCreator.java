package work.managerbe.oauthaccount.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.oauthaccount.domain.OauthAccount;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.repository.OauthAccountRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class OauthAccountCreator {

    private final OauthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;

    /**
     * 사용자와 OAuth 계정을 별도 트랜잭션에서 저장하고 unique 충돌을 즉시 확인한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User create(OAuthUserInfo userInfo) {
        User user = userRepository.save(User.create(
                userInfo.name(),
                userInfo.email(),
                userInfo.profileImageUrl()
        ));

        oauthAccountRepository.saveAndFlush(OauthAccount.create(
                user,
                userInfo.provider(),
                userInfo.providerUserId()
        ));

        return user;
    }
}
