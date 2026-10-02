package work.managerbe.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.board.dto.response.BoardSliceResponse;
import work.managerbe.board.service.BoardService;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.service.ProjectService;
import work.managerbe.global.security.JwtTokenService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 보안 필터와 MVC 인자 리졸버를 통해 Project와 Board API의 인증 연동을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectBoardAuthenticationIntegrationTest {

    private static final String PROJECT_CODE = "WORK";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenService jwtTokenService;

    @MockitoBean
    ProjectService projectService;

    @MockitoBean
    BoardService boardService;

    /**
     * 인증되지 않은 Project API 요청을 서비스 호출 전에 거부하는지 검증한다.
     */
    @Test
    void 인증되지_않은_Project_API_요청은_401을_반환한다() throws Exception {
        // given
        UUID creatorId = UUID.randomUUID();

        // when & then
        mockMvc.perform(get("/{userId}/{code}", creatorId, PROJECT_CODE))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(projectService);
    }

    /**
     * 세션 principal의 UUID를 URL의 생성자 UUID와 구분해 Project 서비스에 전달하는지 검증한다.
     */
    @Test
    void 인증된_Project_API_요청은_principal_UUID를_전달한다() throws Exception {
        // given
        UUID creatorId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 9, 24, 10, 0);
        ProjectResponse response = new ProjectResponse(
                1L, PROJECT_CODE, "업무 관리", 1L, null, now, now);

        when(projectService.get(creatorId, PROJECT_CODE, requesterId)).thenReturn(response);

        // when & then
        mockMvc.perform(get("/{userId}/{code}", creatorId, PROJECT_CODE)
                        .header("Authorization", bearerToken(requesterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value(PROJECT_CODE));

        verify(projectService).get(creatorId, PROJECT_CODE, requesterId);
    }

    /**
     * 인증되지 않은 Board API 요청을 서비스 호출 전에 거부하는지 검증한다.
     */
    @Test
    void 인증되지_않은_Board_API_요청은_401을_반환한다() throws Exception {
        // given
        UUID creatorId = UUID.randomUUID();

        // when & then
        mockMvc.perform(get("/{userId}/{code}/boards", creatorId, PROJECT_CODE))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(boardService);
    }

    /**
     * 세션 principal의 UUID를 URL의 생성자 UUID와 구분해 Board 서비스에 전달하는지 검증한다.
     */
    @Test
    void 인증된_Board_API_요청은_principal_UUID를_전달한다() throws Exception {
        // given
        UUID creatorId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        BoardSliceResponse response = new BoardSliceResponse(List.of(), 0, 20, false);

        when(boardService.getAll(creatorId, PROJECT_CODE, requesterId, 0, 20))
                .thenReturn(response);

        // when & then
        mockMvc.perform(get("/{userId}/{code}/boards", creatorId, PROJECT_CODE)
                        .header("Authorization", bearerToken(requesterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(boardService).getAll(creatorId, PROJECT_CODE, requesterId, 0, 20);
    }

    private String bearerToken(UUID requesterId) {
        return "Bearer " + jwtTokenService.createAccessToken(requesterId);
    }
}
