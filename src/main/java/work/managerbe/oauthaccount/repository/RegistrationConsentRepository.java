package work.managerbe.oauthaccount.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.oauthaccount.domain.RegistrationConsent;

import java.util.UUID;

public interface RegistrationConsentRepository extends JpaRepository<RegistrationConsent, UUID> {
}
