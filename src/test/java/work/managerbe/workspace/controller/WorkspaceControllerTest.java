package work.managerbe.workspace.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import work.managerbe.global.exception.CommonException;
import work.managerbe.global.exception.ErrorCode;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.workspace.dto.request.WorkspaceCreateRequest;
import work.managerbe.workspace.dto.response.WorkspaceResponse;
import work.managerbe.workspace.service.WorkspaceService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 워크스페이스 생성 경로와 요청 전달, 성공 응답 및 공통 예외 응답을 검증한다.
 */
@WebMvcTest(WorkspaceController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkspaceControllerTest {

    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final String PROJECT_CODE = "WORK";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    WorkspaceService workspaceService;

    @AfterEach
    void 인증_정보를_정리한다() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 정상_요청이면_201과_생성된_워크스페이스를_반환한다() throws Exception {
        // given
        authenticate(CREATOR_ID);
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("프로젝트 개발 가이드", "# 개발 가이드");
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 14, 9, 0);
        WorkspaceResponse response = new WorkspaceResponse(
                5L, 1L, request.title(), request.content(), createdAt, createdAt);
        when(workspaceService.create(CREATOR_ID, PROJECT_CODE, CREATOR_ID, request)).thenReturn(response);

        // when / then
        mockMvc.perform(post("/{userId}/{code}/workspaces", CREATOR_ID, PROJECT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.title").value(request.title()))
                .andExpect(jsonPath("$.content").value(request.content()))
                .andExpect(jsonPath("$.createdAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-14T09:00:00"));
        verify(workspaceService).create(CREATOR_ID, PROJECT_CODE, CREATOR_ID, request);
    }

    @Test
    void 요청_본문이_없으면_400을_반환한다() throws Exception {
        // given
        authenticate(CREATOR_ID);

        // when / then
        mockMvc.perform(post("/{userId}/{code}/workspaces", CREATOR_ID, PROJECT_CODE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(workspaceService);
    }

    @Test
    void 프로젝트가_없으면_404를_반환한다() throws Exception {
        // given
        authenticate(CREATOR_ID);
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("제목", "내용");
        when(workspaceService.create(CREATOR_ID, PROJECT_CODE, CREATOR_ID, request))
                .thenThrow(ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

        // when / then
        mockMvc.perform(post("/{userId}/{code}/workspaces", CREATOR_ID, PROJECT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void 생성_권한이_없으면_403을_반환한다() throws Exception {
        // given
        UUID requesterId = UUID.randomUUID();
        authenticate(requesterId);
        WorkspaceCreateRequest request = new WorkspaceCreateRequest("제목", "내용");
        when(workspaceService.create(CREATOR_ID, PROJECT_CODE, requesterId, request))
                .thenThrow(CommonException.of(ErrorCode.FORBIDDEN));

        // when / then
        mockMvc.perform(post("/{userId}/{code}/workspaces", CREATOR_ID, PROJECT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static void authenticate(UUID requesterId) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(requesterId, null));
        SecurityContextHolder.setContext(context);
    }
}
