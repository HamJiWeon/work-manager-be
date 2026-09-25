package work.managerbe.oauthaccount.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import work.managerbe.oauthaccount.domain.RefreshToken;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            delete from RefreshToken token
            where token.expiresAt <= :cutoff
               or token.revokedAt <= :cutoff
            """)
    int deleteExpiredOrRevokedBefore(LocalDateTime cutoff);
}
