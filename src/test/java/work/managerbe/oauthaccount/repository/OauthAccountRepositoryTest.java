package work.managerbe.oauthaccount.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import work.managerbe.global.config.JpaAuditingConfig;
import work.managerbe.global.config.QuerydslConfig;
import work.managerbe.oauthaccount.domain.OAuthProvider;
import work.managerbe.oauthaccount.domain.OauthAccount;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class OauthAccountRepositoryTest {

    @Autowired
    private OauthAccountRepository oauthAccountRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 제공자와 제공자 사용자 ID가 모두 일치하는 OAuth 계정만 조회되는지 검증한다.
     */
    @Test
    void 제공자와_사용자_ID로_OAuth_계정을_조회한다() {
        // given
        User user = userRepository.save(User.create("홍길동", "user@example.com", "image-url"));
        OauthAccount account = oauthAccountRepository.save(
                OauthAccount.create(user, OAuthProvider.GOOGLE, "google-user-id"));

        // when
        var result = oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-user-id");

        // then
        assertThat(result).contains(account);
        assertThat(result.orElseThrow().getUser()).isEqualTo(user);
    }

    /**
     * 제공자 또는 제공자 사용자 ID가 다르면 OAuth 계정이 조회되지 않는지 검증한다.
     */
    @Test
    void 제공자_또는_사용자_ID가_다르면_조회되지_않는다() {
        // given
        User user = userRepository.save(User.create("홍길동", null, null));
        oauthAccountRepository.save(OauthAccount.create(user, OAuthProvider.GOOGLE, "google-user-id"));

        // when & then
        assertThat(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GITHUB, "google-user-id"))
                .isEmpty();
        assertThat(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "other-user-id"))
                .isEmpty();
    }
}
