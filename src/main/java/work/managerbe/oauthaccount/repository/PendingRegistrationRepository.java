package work.managerbe.oauthaccount.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

    /** 조건부 삭제를 한 번의 SQL로 실행하며 다른 요청이 먼저 삭제한 경우에도 정상 처리한다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from PendingRegistration p where p.tokenHash = :tokenHash")
    void deleteByTokenHash(String tokenHash);

    /** 만료된 행을 직접 삭제하고 실제 삭제 건수를 반환하며 이미 소비된 행은 건너뛴다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from PendingRegistration p where p.expiresAt <= :now")
    int deleteByExpiresAtLessThanEqual(LocalDateTime now);
}
