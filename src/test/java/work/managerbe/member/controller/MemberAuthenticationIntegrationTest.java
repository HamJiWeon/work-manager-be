package work.managerbe.member.controller;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.global.security.JwtTokenService;
import work.managerbe.member.dto.MemberPageResponse;
import work.managerbe.member.service.MemberService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 보안 필터를 통해 비인증 차단과 경로 사용자와 다른 JWT 요청자 전달을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class MemberAuthenticationIntegrationTest {
    private final MockMvc mvc;
    private final JwtTokenService tokens;
    @MockitoBean MemberService service;

    @Autowired
    MemberAuthenticationIntegrationTest(MockMvc mvc, JwtTokenService tokens) {
        this.mvc = mvc;
        this.tokens = tokens;
    }

    @Test
    void 비인증_요청은_401로_차단한다() throws Exception {
        // given
        UUID creatorId = UUID.randomUUID();
        // when / then
        mvc.perform(get("/{userId}/{projectId}/members", creatorId, 1L)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void JWT_요청자를_경로의_사용자와_구분해서_전달한다() throws Exception {
        // given
        UUID creatorId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        when(service.getAll(creatorId, 1L, requesterId, 0, 20))
                .thenReturn(new MemberPageResponse(List.of(), 0, 20, 0, 0));
        String token = tokens.createAccessToken(requesterId);
        // when / then
        mvc.perform(get("/{userId}/{projectId}/members", creatorId, 1L).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verify(service).getAll(creatorId, 1L, requesterId, 0, 20);
    }
}
