package work.managerbe.oauthaccount.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.user.domain.User;

import java.time.LocalDateTime;
import java.util.UUID;

/** 가입 시 사용자·소셜 계정·약관 버전별 동의 선택과 서버 기록 시각을 보존한다. */
@Entity
@Getter
@Table(name = "registration_consents", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "term_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistrationConsent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oauth_account_id", nullable = false, updatable = false)
    private OauthAccount oauthAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false, updatable = false)
    private RegistrationTerm term;

    @Column(nullable = false, updatable = false)
    private boolean agreed;

    @Column(nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    /** 소셜 계정의 소유자를 사용해 동의 이력과 회원의 연결을 일치시킨다. */
    public static RegistrationConsent create(OauthAccount account, RegistrationTerm term, boolean agreed, LocalDateTime recordedAt) {
        var consent = new RegistrationConsent();
        consent.user = account.getUser();
        consent.oauthAccount = account;
        consent.term = term;
        consent.agreed = agreed;
        consent.recordedAt = recordedAt;
        return consent;
    }
}
