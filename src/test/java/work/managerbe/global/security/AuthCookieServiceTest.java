package work.managerbe.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieServiceTest {

    private final AuthCookieService service = new AuthCookieService(new JwtProperties(
            "a".repeat(32), Duration.ofMinutes(15), Duration.ofDays(14), true, "/"));

    @Test
    void Refresh_Token만_HttpOnly_Secure_쿠키로_발급한다() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        service.addRefreshToken(response, "refresh");

        // then
        assertThat(response.getHeaders("Set-Cookie"))
                .hasSize(1)
                .allMatch(cookie -> cookie.contains("HttpOnly"))
                .allMatch(cookie -> cookie.contains("Secure"))
                .allMatch(cookie -> cookie.contains("SameSite=Strict"));
    }

    @Test
    void 로그아웃하면_인증_쿠키를_즉시_만료시킨다() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        service.deleteRefreshToken(response);

        // then
        assertThat(response.getHeaders("Set-Cookie"))
                .hasSize(1)
                .allMatch(cookie -> cookie.contains("Max-Age=0"));
    }
}
