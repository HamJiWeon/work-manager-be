package work.managerbe.oauthaccount.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UuidAuthenticationSuccessHandlerTest {

    /**
     * OAuth 로그인 성공 시 내부 UUID principal이 세션에 저장되는지 검증한다.
     */
    @Test
    void 로그인_성공시_UUID_principal을_세션에_저장한다() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        var repository = new HttpSessionSecurityContextRepository();
        var handler = new UuidAuthenticationSuccessHandler(repository);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var oauthPrincipal = new InternalOAuth2User(userId, mock(OAuth2User.class));
        var authentication = new UsernamePasswordAuthenticationToken(oauthPrincipal, null, List.of());

        try {
            // when
            handler.onAuthenticationSuccess(request, response, authentication);

            // then
            var context = repository.loadDeferredContext(request).get();
            assertThat(context.getAuthentication().getPrincipal()).isEqualTo(userId);
            assertThat(context.getAuthentication().isAuthenticated()).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
