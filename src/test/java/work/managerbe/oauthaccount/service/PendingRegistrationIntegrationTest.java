package work.managerbe.oauthaccount.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.oauthaccount.domain.OAuthProvider;
import work.managerbe.oauthaccount.domain.PendingRegistration;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.repository.PendingRegistrationRepository;
import work.managerbe.user.repository.UserRepository;
import work.managerbe.oauthaccount.repository.OauthAccountRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 DB 마이그레이션과 보안 필터로 임시 가입의 권한 분리를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PendingRegistrationIntegrationTest {
    private final PendingRegistrationService service;
    private final PendingRegistrationRepository repository;
    private final UserRepository users;
    private final OauthAccountRepository accounts;
    private final MockMvc mvc;
    private final OAuthUserInfo info = new OAuthUserInfo(OAuthProvider.GITHUB, "new-subject", "신규", null, null);

    @Autowired
    PendingRegistrationIntegrationTest(PendingRegistrationService service, PendingRegistrationRepository repository,
            UserRepository users, OauthAccountRepository accounts, MockMvc mvc) {
        this.service = service; this.repository = repository; this.users = users; this.accounts = accounts; this.mvc = mvc;
    }

    @Test
    void 신규_인증은_임시정보만_저장하며_재인증시_이전_토큰은_폐기한다() {
        // given
        long userCount = users.count();
        long accountCount = accounts.count();
        String previous = service.issue(info, null);
        // when
        String token = service.issue(info, previous);
        // then
        assertThat(service.get(token).name()).isEqualTo("신규");
        assertThatThrownBy(() -> service.get(previous)).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(accounts.count()).isEqualTo(accountCount);
        service.revoke(token);
    }

    @Test
    void 만료된_정보는_조회할수없고_정리시_삭제한다() throws Exception {
        // given
        String token = "expired-pending";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        var expired = repository.save(PendingRegistration.create(info, hash, LocalDateTime.now(java.time.Clock.systemUTC()).minusMinutes(1)));
        // when / then
        assertThatThrownBy(() -> service.get(token)).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        service.cleanup();
        assertThat(repository.findById(expired.getId())).isEmpty();
    }

    @Test
    void 임시가입_쿠키는_보호API와_토큰갱신의_인증수단이_아니다() throws Exception {
        // given
        String token = service.issue(info, null);
        Cookie cookie = new Cookie(AuthCookieService.PENDING_REGISTRATION_COOKIE, token);
        var csrfResponse = mvc.perform(get("/auth/csrf")).andReturn().getResponse();
        Cookie csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
        // when / then
        mvc.perform(get("/auth/registration").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/users/me").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").cookie(cookie, csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/auth/registration").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(delete("/auth/registration").cookie(cookie, csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge(AuthCookieService.PENDING_REGISTRATION_COOKIE, 0));
        mvc.perform(get("/auth/registration").cookie(cookie)).andExpect(status().isUnauthorized());
    }
}
