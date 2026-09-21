package work.managerbe.oauthaccount.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.oauthaccount.domain.OauthAccount;

import java.util.Optional;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, Long> {

    Optional<OauthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}
