package work.managerbe.oauthaccount.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthLoginClientFilterTest {

    private final OAuthRedirectProperties properties = new OAuthRedirectProperties(
            "web", Map.of("web", "https://web.example.com/oauth/callback",
                    "mobile", "manager://oauth/callback"));
    private final OAuthLoginClientFilter filter = new OAuthLoginClientFilter(properties);

    @Test
    void OAuth_로그인_요청의_모바일_클라이언트를_세션에_저장한다() throws Exception {
        // given
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setParameter("client", "mobile");
        var response = new MockHttpServletResponse();
        var filterChain = new MockFilterChain();

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute(
                OAuthLoginClientFilter.OAUTH_CLIENT_SESSION_ATTRIBUTE)).isEqualTo("mobile");
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void 클라이언트가_없으면_기본_웹_클라이언트를_세션에_저장한다() throws Exception {
        // given
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        var response = new MockHttpServletResponse();

        // when
        filter.doFilter(request, response, new MockFilterChain());

        // then
        assertThat(request.getSession(false).getAttribute(
                OAuthLoginClientFilter.OAUTH_CLIENT_SESSION_ATTRIBUTE)).isEqualTo("web");
    }

    @Test
    void 허용하지_않은_클라이언트는_400으로_거부한다() throws Exception {
        // given
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setParameter("client", "attacker");
        var response = new MockHttpServletResponse();
        var filterChain = new MockFilterChain();

        // when
        filter.doFilter(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(filterChain.getRequest()).isNull();
    }

    @Test
    void OAuth_시작_경로가_아니면_클라이언트_검증을_건너뛴다() throws Exception {
        // given
        var request = new MockHttpServletRequest("GET", "/auth/refresh");
        var filterChain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        // then
        assertThat(request.getSession(false)).isNull();
        assertThat(filterChain.getRequest()).isSameAs(request);
    }
}
