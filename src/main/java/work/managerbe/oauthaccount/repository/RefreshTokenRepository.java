package work.managerbe.oauthaccount.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.oauthaccount.domain.RefreshToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 로그아웃 대상 토큰을 잠그지 않고 세션 식별자만 조회한다.
     */
    @Query("select token.sessionId from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<UUID> findSessionIdByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 동일 세션의 토큰을 일정한 순서로 잠가 토큰 회전과 세션 폐기를 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.sessionId = :sessionId order by token.id")
    List<RefreshToken> lockAllBySessionId(@Param("sessionId") UUID sessionId);

    /**
     * 잠긴 로그인 세션에서 회전된 모든 활성 Refresh Token을 한 번에 폐기한다.
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
