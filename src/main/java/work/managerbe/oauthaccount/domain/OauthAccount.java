package work.managerbe.oauthaccount.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.global.base.CreateEntity;
import work.managerbe.user.domain.User;

@Entity
@Getter
@Table(name = "oauth_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OauthAccount extends CreateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String providerUserId;

    @Builder
    private OauthAccount(User user, String provider, String providerUserId) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static OauthAccount create(User user, String provider, String providerUserId) {
        return OauthAccount.builder()
                .user(user)
                .provider(provider)
                .providerUserId(providerUserId)
                .build();
    }

}
