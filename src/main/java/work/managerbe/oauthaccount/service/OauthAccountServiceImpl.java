package work.managerbe.oauthaccount.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.oauthaccount.domain.OauthAccount;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.repository.OauthAccountRepository;
import work.managerbe.user.domain.User;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OauthAccountServiceImpl implements OauthAccountService {

    private final OauthAccountRepository oauthAccountRepository;
    private final OauthAccountCreator oauthAccountCreator;

    @Override
    public User findOrCreate(OAuthUserInfo userInfo) {
        return findUser(userInfo).orElseGet(() -> createOrFindUser(userInfo));
    }

    /**
     * 동시 최초 로그인으로 unique 충돌이 발생하면 먼저 생성된 계정을 재조회한다.
     */
    private User createOrFindUser(OAuthUserInfo userInfo) {
        try {
            return oauthAccountCreator.create(userInfo);
        } catch (DataIntegrityViolationException exception) {
            return findUser(userInfo).orElseThrow(() -> exception);
        }
    }

    private Optional<User> findUser(OAuthUserInfo userInfo) {
        return oauthAccountRepository.findByProviderAndProviderUserId(
                        userInfo.provider(), userInfo.providerUserId())
                .map(OauthAccount::getUser);
    }
}
