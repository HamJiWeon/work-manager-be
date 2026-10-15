package work.managerbe.oauthaccount.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import work.managerbe.oauthaccount.domain.PendingRegistration;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, UUID> {
    Optional<PendingRegistration> findByTokenHashAndExpiresAtAfter(String tokenHash, LocalDateTime now);
    /** 동일 임시 토큰의 가입 완료 요청을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingRegistration> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
    long deleteByExpiresAtLessThanEqual(LocalDateTime now);
}
