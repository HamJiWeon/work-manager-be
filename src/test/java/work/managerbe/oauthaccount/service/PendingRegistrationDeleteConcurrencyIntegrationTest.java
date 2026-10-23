package work.managerbe.oauthaccount.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.oauthaccount.domain.*;
import work.managerbe.oauthaccount.dto.request.*;
import work.managerbe.oauthaccount.repository.*;
import work.managerbe.user.repository.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 실제 PostgreSQL에서 가입 완료와 취소·재인증·만료 정리가 경합해도 정상 처리되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PendingRegistrationDeleteConcurrencyIntegrationTest {
    private static final int WAIT_TIMEOUT_SECONDS = 20;
    private static final int POLL_INTERVAL_MILLIS = 20;
    private static final long EXPIRED_AFTER_SECONDS = 660;
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private final PendingRegistrationService pending;
    private final RegistrationCompletionService completion;
    private final RegistrationTermRepository terms;
    private final RegistrationConsentRepository consents;
    private final OauthAccountRepository accounts;
    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final PendingRegistrationRepository pendingRepository;
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;
    @MockitoSpyBean
    private RefreshTokenService refresh;
    @MockitoSpyBean
    private Clock clock;
    private final OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GOOGLE, "delete-race-user", "삭제 경합 검증", null, null);
    private final RegistrationCompleteRequest accepted = new RegistrationCompleteRequest(List.of(
            new RegistrationCompleteRequest.Consent("privacy", "v1", true),
            new RegistrationCompleteRequest.Consent("terms", "v1", true)));

    @Autowired
    PendingRegistrationDeleteConcurrencyIntegrationTest(PendingRegistrationService pending,
            RegistrationCompletionService completion, RegistrationTermRepository terms,
            RegistrationConsentRepository consents, OauthAccountRepository accounts,
            UserRepository users, RefreshTokenRepository tokens, PendingRegistrationRepository pendingRepository,
            JdbcTemplate jdbc, MockMvc mvc) {
        this.pending = pending;
        this.completion = completion;
        this.terms = terms;
        this.consents = consents;
        this.accounts = accounts;
        this.users = users;
        this.tokens = tokens;
        this.pendingRepository = pendingRepository;
        this.jdbc = jdbc;
        this.mvc = mvc;
    }

    @BeforeEach
    void setUp() {
        consents.deleteAll(); tokens.deleteAll(); accounts.deleteAll(); users.deleteAll();
        pendingRepository.deleteAll(); terms.deleteAll();
        terms.saveAllAndFlush(List.of(
                RegistrationTerm.create("privacy", "v1", "테스트 개인정보", "테스트 본문", true),
                RegistrationTerm.create("terms", "v1", "테스트 이용약관", "테스트 본문", true)));
    }

    @Test
    void 가입완료와_취소가_겹쳐도_취소는_정상응답한다() throws Exception {
        // given
        var csrfResponse = mvc.perform(get("/auth/csrf")).andReturn().getResponse();
        Cookie csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
        // when
        int result = race(false, token -> mvc.perform(delete("/auth/registration")
                .cookie(new Cookie(AuthCookieService.PENDING_REGISTRATION_COOKIE, token), csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())).andReturn().getResponse().getStatus());
        // then
        assertThat(result).isEqualTo(204);
        assertThat(pendingRepository.count()).isZero();
        assertThat(users.count()).isEqualTo(1);
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void 가입완료와_재인증이_겹쳐도_새_임시상태가_저장된다() throws Exception {
        // given / when
        String token = race(false, previous -> pending.issue(info, previous));
        // then
        assertThat(pendingRepository.count()).isEqualTo(1);
        assertThat(pending.get(token).name()).isEqualTo(info.name());
        assertThat(users.count()).isEqualTo(1);
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void 가입완료와_만료정리가_겹쳐도_정리는_정상처리한다() throws Exception {
        // given / when
        long deleted = race(true, token -> pending.cleanup());
        // then
        assertThat(deleted).isZero();
        assertThat(pendingRepository.count()).isZero();
        assertThat(users.count()).isEqualTo(1);
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void 이미_삭제된_토큰의_취소와_만료정리는_반복해도_정상처리한다() {
        // given
        String token = pending.issue(info, null);
        pending.revoke(token);
        // when
        pending.revoke(token);
        long deleted = pending.cleanup();
        // then
        assertThat(deleted).isZero();
        assertThat(pendingRepository.count()).isZero();
    }

    private <T> T race(boolean expire, PendingAction<T> action) throws Exception {
        String token = pending.issue(info, null);
        var completionPaused = new CountDownLatch(1);
        var releaseCompletion = new CountDownLatch(1);
        doAnswer(invocation -> {
            completionPaused.countDown();
            if (!releaseCompletion.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) throw new IllegalStateException("pause timeout");
            return invocation.callRealMethod();
        }).when(refresh).issue(any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var signup = executor.submit(() -> completion.complete(token, accepted));
                assertThat(completionPaused.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                if (expire) {
                    Instant expiredNow = clock.instant().plusSeconds(EXPIRED_AFTER_SECONDS);
                    doReturn(expiredNow).when(clock).instant();
                }
                var other = executor.submit(() -> action.run(token));
                boolean waiting = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_TIMEOUT_SECONDS);
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS(SELECT 1 FROM pg_stat_activity
                            WHERE wait_event_type = 'Lock' AND state = 'active'
                            AND lower(query) LIKE '%delete%pending_registrations%'
                            AND pid <> pg_backend_pid())
                            """, Boolean.class));
                    if (waiting || other.isDone()) break;
                    Thread.sleep(POLL_INTERVAL_MILLIS);
                }
                assertThat(waiting).as("벌크 DELETE가 가입 트랜잭션 잠금을 기다려야 한다").isTrue();
                releaseCompletion.countDown();
                assertThat(signup.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS).refreshToken()).isNotBlank();
                assertThat(users.count()).isEqualTo(1);
                return other.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } finally { releaseCompletion.countDown(); }
        }
    }

    @FunctionalInterface
    interface PendingAction<T> { T run(String token) throws Exception; }
}
