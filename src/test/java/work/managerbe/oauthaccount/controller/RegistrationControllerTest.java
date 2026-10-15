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

    @Autowired
    RegistrationControllerTest(MockMvc mvc) { this.mvc = mvc; }

    @Test
    void 임시_쿠키로_가입_상태를_조회하며_민감정보는_노출하지_않는다() throws Exception {
        // given
        when(service.get("pending")).thenReturn(new PendingRegistrationResponse("신규", OAuthProvider.GOOGLE,
                LocalDateTime.of(2026, 10, 15, 0, 10)));
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
