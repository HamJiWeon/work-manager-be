package work.managerbe.oauthaccount.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import work.managerbe.global.config.JpaAuditingConfig;
import work.managerbe.global.config.QuerydslConfig;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class RefreshTokenRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void Refresh_Token_해시는_정확히_64자여야_한다() {
        // given
        User user = userRepository.save(User.create("홍길동", null, null));
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO refresh_tokens
                            (id, user_id, session_id, token_hash, expires_at, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), user.getId(), UUID.randomUUID(),
                        "short-hash", now.plusDays(1), now))
                .hasMessageContaining("CK_REFRESH_TOKENS_TOKEN_HASH_LENGTH");
    }
}
