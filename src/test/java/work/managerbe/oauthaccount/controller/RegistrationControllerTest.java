package work.managerbe.oauthaccount.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.oauthaccount.domain.OAuthProvider;
import work.managerbe.oauthaccount.dto.response.PendingRegistrationResponse;
import work.managerbe.oauthaccount.service.PendingRegistrationService;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegistrationControllerTest {
    private final MockMvc mvc;
    @MockitoBean
    private PendingRegistrationService service;
    @MockitoBean
    private AuthCookieService cookies;
    @MockitoBean
    private work.managerbe.oauthaccount.service.RegistrationCompletionService completionService;

    @Autowired
    RegistrationControllerTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void 임시_쿠키로_가입_상태를_조회하며_민감정보는_노출하지_않는다() throws Exception {
        // given
        when(service.get("pending")).thenReturn(new PendingRegistrationResponse("신규", OAuthProvider.GOOGLE,
                LocalDateTime.of(2026, 10, 15, 0, 10), java.util.List.of()));
        // when / then
        mvc.perform(get("/auth/registration").cookie(new Cookie(AuthCookieService.PENDING_REGISTRATION_COOKIE, "pending")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.name").value("신규")).andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.providerUserId").doesNotExist());
    }

    @Test
    void 유효하지_않은_쿠키는_401을_반환한다() throws Exception {
        // given
        when(service.get(null)).thenThrow(new BadCredentialsException("missing"));
        // when / then
        mvc.perform(get("/auth/registration")).andExpect(status().isUnauthorized());
    }

    /** 프론트엔드의 동의 제출 형식을 수용하고 커밋 결과로 로그인 쿠키를 발급한다. */
    @Test
    void 가입완료시_로그인_쿠키를_발급하고_임시쿠키를_삭제한다() throws Exception {
        // given
        when(completionService.complete(eq("pending"), any()))
                .thenReturn(new work.managerbe.global.security.TokenPair("access", "refresh"));
        // when / then
        mvc.perform(post("/auth/registration")
                        .cookie(new Cookie(AuthCookieService.PENDING_REGISTRATION_COOKIE, "pending"))
                        .contentType("application/json")
                        .content("{\"consents\":[{\"id\":\"terms\",\"version\":\"v1\",\"agreed\":true}]}"))
                .andExpect(status().isNoContent());
        verify(cookies).addRefreshToken(any(), eq("refresh"));
        verify(cookies).deletePendingRegistration(any());
    }

    @Test
    void 동의_선택이_없으면_가입서비스를_호출하지않는다() throws Exception {
        // given / when / then
        mvc.perform(post("/auth/registration").contentType("application/json")
                        .content("{\"consents\":[{\"id\":\"terms\",\"version\":\"v1\"}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(completionService, cookies);
    }

    @Test
    void 취소하면_서버_정보와_쿠키를_제거한다() throws Exception {
        // given / when
        mvc.perform(delete("/auth/registration").cookie(new Cookie(AuthCookieService.PENDING_REGISTRATION_COOKIE, "pending")))
                .andExpect(status().isNoContent());
        // then
        verify(service).revoke("pending");
        verify(cookies).deletePendingRegistration(any());
    }
}
