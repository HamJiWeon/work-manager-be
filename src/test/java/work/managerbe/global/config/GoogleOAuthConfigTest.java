package work.managerbe.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "oauth"})
@TestPropertySource(properties = {
        "GOOGLE_CLIENT_ID=test-google-client-id",
        "GOOGLE_CLIENT_SECRET=test-google-client-secret",
        "spring.datasource.url=jdbc:h2:mem:google-oauth;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class GoogleOAuthConfigTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * OAuth 프로필에서 Google 로그인 진입점이 Google 인증 요청으로 연결되는지 검증한다.
     */
    @Test
    void Google_로그인_진입점은_인증_페이지로_리다이렉트한다() throws Exception {
        // when & then
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "https://accounts.google.com/o/oauth2/v2/auth")));
    }
}
