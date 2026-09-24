package work.managerbe.oauthaccount.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.oauthaccount.domain.OAuthProvider;
import work.managerbe.oauthaccount.domain.OauthAccount;

import java.util.Optional;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, Long> {

    /**
     * OAuth 제공자와 제공자 측 사용자 식별자가 모두 일치하는 계정을 조회한다.
     */
    Optional<OauthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
