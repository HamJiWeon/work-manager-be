package work.managerbe.oauthaccount.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;

import java.time.LocalDateTime;
import java.util.UUID;

/** 회원 생성 없이 소셜 인증 정보와 만료 시각을 별도 저장한다. */
@Entity
@Getter
@Table(name = "pending_registrations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(nullable = false)
    private String providerUserId;

    @Column(nullable = false)
    private String name;

    private String email;
    private String profileImageUrl;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /** 검증된 소셜 프로필과 토큰 해시로 만료 가능한 임시 가입 정보를 생성한다. */
    public static PendingRegistration create(OAuthUserInfo info, String tokenHash, LocalDateTime expiresAt) {
        var pending = new PendingRegistration();
        pending.provider = info.provider();
        pending.providerUserId = info.providerUserId();
        pending.name = info.name();
        pending.email = info.email();
        pending.profileImageUrl = info.profileImageUrl();
        pending.tokenHash = tokenHash;
        pending.expiresAt = expiresAt;
        return pending;
    }
}
