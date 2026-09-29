package work.managerbe.oauthaccount.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.BadCredentialsException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import work.managerbe.oauthaccount.repository.RefreshTokenRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "security.jwt.secret=manager-be-test-secret-at-least-32-bytes"
})
@Testcontainers
class RefreshTokenConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Autowired
    RefreshTokenConcurrencyIntegrationTest(
            RefreshTokenService refreshTokenService,
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository
    ) {
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 동일한_Refresh_Token을_동시에_회전하면_하나만_성공한다() throws Exception {
        // given
        User user = userRepository.save(User.create("홍길동", "user@example.com", null));
        String refreshToken = refreshTokenService.issue(user).refreshToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> rotate = () -> {
            ready.countDown();
            start.await();
            try {
                refreshTokenService.rotate(refreshToken);
                return true;
            } catch (BadCredentialsException exception) {
                return false;
            }
        };

        // when
        List<Boolean> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(rotate);
            Future<Boolean> second = executor.submit(rotate);
            ready.await();
            start.countDown();
            results = List.of(first.get(), second.get());
        }

        // then
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
    }

    /**
     * 비관적 잠금이 만드는 회전 후 로그아웃 순서에서 새 토큰까지 폐기되는지 검증한다.
     */
    @Test
    void 회전보다_늦게_처리된_로그아웃은_새_Refresh_Token도_폐기한다() {
        // given
        User user = userRepository.save(User.create("홍길동", "logout@example.com", null));
        String oldRefreshToken = refreshTokenService.issue(user).refreshToken();
        String newRefreshToken = refreshTokenService.rotate(oldRefreshToken).refreshToken();

        // when
        refreshTokenService.revoke(oldRefreshToken);

        // then
        assertThatThrownBy(() -> refreshTokenService.rotate(newRefreshToken))
                .isInstanceOf(BadCredentialsException.class);
    }

    /**
     * 회전과 로그아웃이 동시에 시작되어도 로그아웃 완료 후 해당 세션의 토큰이 남지 않는지 검증한다.
     */
    @Test
    void 이전_Refresh_Token_로그아웃과_현재_토큰_회전이_경합해도_세션은_폐기된다() throws Exception {
        // given
        User user = userRepository.save(User.create("홍길동", "race@example.com", null));
        String oldRefreshToken = refreshTokenService.issue(user).refreshToken();
        String currentRefreshToken = refreshTokenService.rotate(oldRefreshToken).refreshToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<String> rotate = () -> {
            ready.countDown();
            start.await();
            try {
                return refreshTokenService.rotate(currentRefreshToken).refreshToken();
            } catch (BadCredentialsException exception) {
                return null;
            }
        };
        Callable<Void> logout = () -> {
            ready.countDown();
            start.await();
            refreshTokenService.revoke(oldRefreshToken);
            return null;
        };

        // when
        String rotatedRefreshToken;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> rotation = executor.submit(rotate);
            Future<Void> revocation = executor.submit(logout);
            ready.await();
            start.countDown();
            rotatedRefreshToken = rotation.get();
            revocation.get();
        }

        // then
        assertThatThrownBy(() -> refreshTokenService.rotate(oldRefreshToken))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(currentRefreshToken))
                .isInstanceOf(BadCredentialsException.class);
        if (rotatedRefreshToken != null) {
            assertThatThrownBy(() -> refreshTokenService.rotate(rotatedRefreshToken))
                    .isInstanceOf(BadCredentialsException.class);
        }
        assertThat(refreshTokenRepository.findAll())
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
    }
}
