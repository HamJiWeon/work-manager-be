package work.managerbe.oauthaccount.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import work.managerbe.global.exception.CommonException;
import work.managerbe.global.exception.ErrorCode;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.oauthaccount.domain.*;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.dto.request.RegistrationCompleteRequest;
import work.managerbe.oauthaccount.repository.*;
import work.managerbe.user.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 PostgreSQL과 보안 필터로 동의 검증·원자적 가입·중복 제출 방지를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RegistrationCompletionIntegrationTest {
    private static final int LOCK_WAIT_TIMEOUT_SECONDS = 15;
    private static final int LOCK_POLL_INTERVAL_MILLIS = 20;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private final PendingRegistrationService pending;
    private final RegistrationCompletionService completion;
    private final PendingRegistrationRepository pendingRepository;
    private final RegistrationTermRepository terms;
    private final RegistrationConsentRepository consents;
    private final UserRepository users;
    private final OauthAccountRepository accounts;
    private final RefreshTokenRepository tokens;
    private final MockMvc mvc;
    private final TransactionTemplate transaction;
    @MockitoSpyBean
    private RefreshTokenService refresh;

    private final OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GOOGLE, "new-social-id", "신규", "new@example.com", null);
    private final RegistrationCompleteRequest accepted = new RegistrationCompleteRequest(List.of(
            new RegistrationCompleteRequest.Consent("terms", "v1", true),
            new RegistrationCompleteRequest.Consent("privacy", "v1", true),
            new RegistrationCompleteRequest.Consent("news", "v1", false)));

    @Autowired
    RegistrationCompletionIntegrationTest(PendingRegistrationService pending, RegistrationCompletionService completion,
            PendingRegistrationRepository pendingRepository, RegistrationTermRepository terms, RegistrationConsentRepository consents,
            UserRepository users, OauthAccountRepository accounts, RefreshTokenRepository tokens, MockMvc mvc,
            PlatformTransactionManager manager, DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.pending = pending; this.completion = completion; this.pendingRepository = pendingRepository;
        this.terms = terms; this.consents = consents; this.users = users; this.accounts = accounts;
        this.tokens = tokens; this.mvc = mvc; this.transaction = new TransactionTemplate(manager);
    }

    @BeforeEach
    void setUp() {
        consents.deleteAll(); tokens.deleteAll(); accounts.deleteAll(); users.deleteAll();
        pendingRepository.deleteAll(); terms.deleteAll();
        terms.saveAll(List.of(
                RegistrationTerm.create("terms", "v1", "테스트 이용약관", "테스트 전용 본문", true),
                RegistrationTerm.create("privacy", "v1", "테스트 개인정보 안내", "테스트 전용 본문", true),
                RegistrationTerm.create("news", "v1", "테스트 소식 수신", "테스트 전용 본문", false)));
    }

    @Test
    void 조회시_실제_약관_본문과_필수여부_버전을_제공한다() throws Exception {
        // given
        String token = pending.issue(info, null);
        // when / then
        mvc.perform(get("/auth/registration").cookie(new Cookie(AuthCookieService.PENDING_REGISTRATION_COOKIE, token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.terms.length()").value(3))
                .andExpect(jsonPath("$.terms[0].id").value("news"))
                .andExpect(jsonPath("$.terms[0].required").value(false))
                .andExpect(jsonPath("$.terms[0].version").value("v1"))
                .andExpect(jsonPath("$.terms[0].content").value("테스트 전용 본문"));
    }

    @Test
    void 필수동의후_회원_소셜계정_선택별동의_시각을_함께_저장한다() {
        // given
        String token = pending.issue(info, null);
        LocalDateTime before = LocalDateTime.now(Clock.systemUTC());
        // when
        var result = completion.complete(token, accepted);
        // then
        assertThat(users.count()).isEqualTo(1);
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(tokens.count()).isEqualTo(1);
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(pendingRepository.count()).isZero();
        assertThatThrownBy(() -> pending.get(token)).isInstanceOf(BadCredentialsException.class);
        transaction.executeWithoutResult(status -> {
            var saved = consents.findAll();
            assertThat(saved).hasSize(3);
            assertThat(saved).extracting(c -> c.getTerm().getCode(), RegistrationConsent::isAgreed)
                    .containsExactlyInAnyOrder(tuple("terms", true), tuple("privacy", true), tuple("news", false));
            assertThat(saved).allSatisfy(c -> {
                assertThat(c.getUser().getId()).isEqualTo(c.getOauthAccount().getUser().getId());
                assertThat(c.getTerm().getVersion()).isEqualTo("v1");
                assertThat(c.getRecordedAt()).isBetween(before.minusSeconds(1), LocalDateTime.now(Clock.systemUTC()).plusSeconds(1));
            });
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"consents\":[{\"id\":\"terms\",\"version\":\"v1\",\"agreed\":true}]}",
            "{\"consents\":[{\"id\":\"terms\",\"version\":\"v1\",\"agreed\":false},{\"id\":\"privacy\",\"version\":\"v1\",\"agreed\":true}]}",
            "{\"consents\":[{\"id\":\"terms\",\"version\":\"old\",\"agreed\":true},{\"id\":\"privacy\",\"version\":\"v1\",\"agreed\":true}]}",
            "{\"consents\":[{\"id\":\"terms\",\"version\":\"v1\",\"agreed\":true},{\"id\":\"terms\",\"version\":\"v1\",\"agreed\":true}]}",
            "{\"consents\":[{\"id\":\"unknown\",\"version\":\"v1\",\"agreed\":true}]}"
    })
    void 필수누락_거부_구버전_중복_알수없는약관은_가입을_차단한다(String body) throws Exception {
        // given
        String token = pending.issue(info, null);
        var csrfResponse = mvc.perform(get("/auth/csrf")).andReturn().getResponse();
        Cookie csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
        // when / then
        mvc.perform(post("/auth/registration").cookie(new Cookie(AuthCookieService.PENDING_REGISTRATION_COOKIE, token), csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isZero(); assertThat(accounts.count()).isZero();
        assertThat(consents.count()).isZero(); assertThat(tokens.count()).isZero();
        assertThat(pending.get(token).name()).isEqualTo("신규");
    }

    @Test
    void 선택동의_생략은_미동의로_저장한다() {
        // given
        String token = pending.issue(info, null);
        var request = new RegistrationCompleteRequest(List.of(
                new RegistrationCompleteRequest.Consent("terms", "v1", true),
                new RegistrationCompleteRequest.Consent("privacy", "v1", true)));
        // when
        completion.complete(token, request);
        // then
        transaction.executeWithoutResult(status -> assertThat(consents.findAll())
                .filteredOn(c -> c.getTerm().getCode().equals("news"))
                .singleElement().extracting(RegistrationConsent::isAgreed).isEqualTo(false));
    }

    @Test
    void 선택동의_승인도_그대로_저장한다() {
        // given
        String token = pending.issue(info, null);
        var request = new RegistrationCompleteRequest(List.of(
                new RegistrationCompleteRequest.Consent("terms", "v1", true),
                new RegistrationCompleteRequest.Consent("privacy", "v1", true),
                new RegistrationCompleteRequest.Consent("news", "v1", true)));
        // when
        completion.complete(token, request);
        // then
        transaction.executeWithoutResult(status -> assertThat(consents.findAll())
                .allMatch(RegistrationConsent::isAgreed));
    }

    @Test
    void 개정후_구버전_동의는_거절하고_기존본문은_보존한다() {
        // given
        String token = pending.issue(info, null);
        transaction.executeWithoutResult(status -> {
            var old = terms.findByActiveTrueOrderByCodeAsc().stream()
                    .filter(term -> term.getCode().equals("terms")).findFirst().orElseThrow();
            old.deactivate();
            terms.save(RegistrationTerm.create("terms", "v2", "테스트 개정약관", "개정된 테스트 본문", true));
        });
        // when / then
        assertThatThrownBy(() -> completion.complete(token, accepted)).isInstanceOfSatisfying(CommonException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThat(pending.get(token).terms()).filteredOn(term -> term.id().equals("terms"))
                .singleElement().extracting(work.managerbe.oauthaccount.dto.response.RegistrationTermResponse::version)
                .isEqualTo("v2");
        assertThat(terms.findAll()).filteredOn(term -> term.getCode().equals("terms") && term.getVersion().equals("v1"))
                .singleElement().extracting(RegistrationTerm::getContent).isEqualTo("테스트 전용 본문");
        assertThat(users.count()).isZero();
    }

    @Test
    void 같은_소셜계정의_다른_임시토큰으로_중복가입할수없다() {
        // given
        String first = pending.issue(info, null);
        String second = pending.issue(info, null);
        completion.complete(first, accepted);
        // when / then
        assertThatThrownBy(() -> completion.complete(second, accepted)).isInstanceOfSatisfying(CommonException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(users.count()).isEqualTo(1); assertThat(accounts.count()).isEqualTo(1);
        assertThat(consents.count()).isEqualTo(3); assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void 저장중_실패하면_회원_동의_토큰을_롤백하고_임시상태를_보존한다() {
        // given
        String token = pending.issue(info, null);
        doThrow(new IllegalStateException("test failure")).when(refresh).issue(any());
        // when / then
        assertThatThrownBy(() -> completion.complete(token, accepted)).isInstanceOf(IllegalStateException.class);
        assertThat(users.count()).isZero(); assertThat(accounts.count()).isZero();
        assertThat(consents.count()).isZero(); assertThat(tokens.count()).isZero();
        assertThat(pending.get(token).name()).isEqualTo("신규");
    }

    /** 개정 중 기존 행이 조회에서 사라져도 필수 동의를 누락한 가입을 거부한다. */
    @Test
    void 약관개정과_경합해도_필수동의_누락을_거절한다() throws Exception {
        // given
        var request = new RegistrationCompleteRequest(List.of(
                new RegistrationCompleteRequest.Consent("privacy", "v1", true)));
        // when / then
        assertThatThrownBy(() -> reviseDuringSignup(request))
                .isInstanceOfSatisfying(ExecutionException.class, failure ->
                        assertThat(failure.getCause()).isInstanceOfSatisfying(CommonException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)));
        assertThat(users.count()).isZero(); assertThat(accounts.count()).isZero();
        assertThat(consents.count()).isZero(); assertThat(tokens.count()).isZero();
        assertThat(pendingRepository.count()).isEqualTo(1);
    }

    /** 충돌 후 재조회한 최신 버전과 제출 버전이 다르면 가입을 거부한다. */
    @Test
    void 약관개정과_경합하면_구버전_동의를_거절한다() throws Exception {
        // given / when / then
        assertThatThrownBy(() -> reviseDuringSignup(accepted))
                .isInstanceOfSatisfying(ExecutionException.class, failure ->
                        assertThat(failure.getCause()).isInstanceOfSatisfying(CommonException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)));
        assertThat(users.count()).isZero(); assertThat(consents.count()).isZero();
        assertThat(tokens.count()).isZero();
    }

    /** 개정 충돌로 첫 트랜잭션을 롤백한 뒤 최신 동의로 재시도하면 한 번만 저장한다. */
    @Test
    void 약관개정과_경합해도_최신_동의는_재시도후_정확히한번_저장한다() throws Exception {
        // given
        var request = new RegistrationCompleteRequest(List.of(
                new RegistrationCompleteRequest.Consent("privacy", "v1", true),
                new RegistrationCompleteRequest.Consent("terms", "v2", true)));
        // when
        var result = reviseDuringSignup(request);
        // then
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(users.count()).isEqualTo(1); assertThat(accounts.count()).isEqualTo(1);
        assertThat(consents.count()).isEqualTo(3); assertThat(tokens.count()).isEqualTo(1);
        assertThat(pendingRepository.count()).isZero();
        transaction.executeWithoutResult(status -> assertThat(consents.findAll())
                .filteredOn(c -> c.getTerm().getCode().equals("terms"))
                .singleElement().satisfies(c -> {
                    assertThat(c.getTerm().getVersion()).isEqualTo("v2");
                    assertThat(c.isAgreed()).isTrue();
                }));
    }

    private work.managerbe.global.security.TokenPair reviseDuringSignup(RegistrationCompleteRequest request) throws Exception {
        String token = pending.issue(info, null);
        try (var revision = dataSource.getConnection()) {
            revision.setAutoCommit(false);
            try (var executor = Executors.newSingleThreadExecutor()) {
                try {
                    try (var update = revision.prepareStatement(
                            "UPDATE registration_terms SET active = false WHERE code = 'terms' AND version = 'v1'")) {
                        update.executeUpdate();
                    }
                    try (var insert = revision.prepareStatement("""
                            INSERT INTO registration_terms(id,code,version,title,content,required,active)
                            VALUES (?,'terms','v2','테스트 개정약관','테스트 개정본문',true,true)
                            """)) {
                        insert.setObject(1, UUID.randomUUID());
                        insert.executeUpdate();
                    }
                    var signup = executor.submit(() -> completion.complete(token, request));
                    boolean waiting = false;
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LOCK_WAIT_TIMEOUT_SECONDS);
                    while (System.nanoTime() < deadline) {
                        waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                                SELECT EXISTS(SELECT 1 FROM pg_stat_activity
                                WHERE wait_event_type = 'Lock' AND state = 'active'
                                AND query LIKE '%registration_terms%'
                                AND pid <> pg_backend_pid())
                                """, Boolean.class));
                        if (waiting || signup.isDone()) break;
                        Thread.sleep(LOCK_POLL_INTERVAL_MILLIS);
                    }
                    assertThat(waiting).as("가입 조회가 실제 약관 행 잠금 대기에 진입해야 한다").isTrue();
                    revision.commit();
                    return signup.get(LOCK_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } finally {
                    revision.rollback();
                }
            }
        }
    }

    @Test
    void 같은_임시토큰의_동시가입은_한번만_성공한다() throws Exception {
        // given
        String token = pending.issue(info, null);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> submit = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            try { completion.complete(token, accepted); return true; }
            catch (BadCredentialsException exception) { return false; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            // when
            start.countDown();
            // then
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(users.count()).isEqualTo(1); assertThat(accounts.count()).isEqualTo(1);
            assertThat(consents.count()).isEqualTo(3); assertThat(tokens.count()).isEqualTo(1);
        }
    }

    @Test
    void 약관이_준비되지않았으면_가입을_차단한다() {
        // given
        terms.deleteAll();
        String token = pending.issue(info, null);
        // when / then
        assertThatThrownBy(() -> completion.complete(token, accepted)).isInstanceOfSatisfying(CommonException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REGISTRATION_TERMS_UNAVAILABLE));
        assertThat(users.count()).isZero();
    }

    @Test
    void POST는_CSRF가_필요하고_성공후_갱신쿠키로_내정보를_조회한다() throws Exception {
        // given
        String token = pending.issue(info, null);
        Cookie pendingCookie = new Cookie(AuthCookieService.PENDING_REGISTRATION_COOKIE, token);
        mvc.perform(post("/auth/registration").cookie(pendingCookie).contentType("application/json")
                        .content("{\"consents\":[]}"))
                .andExpect(status().isForbidden());
        var csrfResponse = mvc.perform(get("/auth/csrf")).andReturn().getResponse();
        Cookie csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
        // when
        var response = mvc.perform(post("/auth/registration").cookie(pendingCookie, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()).contentType("application/json")
                        .content("{\"consents\":[{\"id\":\"terms\",\"version\":\"v1\",\"agreed\":true},{\"id\":\"privacy\",\"version\":\"v1\",\"agreed\":true}]}"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookieService.PENDING_REGISTRATION_COOKIE, 0)).andReturn().getResponse();
        // then
        Cookie refreshCookie = response.getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE);
        assertThat(refreshCookie).isNotNull();
        var userId = users.findAll().getFirst().getId();
        mvc.perform(get("/users/me").header("Authorization", "Bearer " + refresh.rotate(refreshCookie.getValue()).accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(userId.toString()));
    }
}
