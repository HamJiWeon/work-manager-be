package work.managerbe.oauthaccount.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.oauthaccount.domain.OauthAccount;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, Long> {
}
