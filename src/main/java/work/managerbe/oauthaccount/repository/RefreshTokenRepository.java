package work.managerbe.oauthaccount.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.oauthaccount.domain.RefreshToken;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 동일 로그인 세션에서 회전된 모든 활성 Refresh Token을 한 번에 폐기한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken token
            set token.revokedAt = :revokedAt
            where token.sessionId = :sessionId
              and token.revokedAt is null
            """)
    int revokeAllBySessionId(
            @Param("sessionId") UUID sessionId,
            @Param("revokedAt") LocalDateTime revokedAt
    );

    @Modifying
    @Query("""
            delete from RefreshToken token
            where token.expiresAt <= :cutoff
               or token.revokedAt <= :cutoff
            """)
    int deleteExpiredOrRevokedBefore(@Param("cutoff") LocalDateTime cutoff);
}
