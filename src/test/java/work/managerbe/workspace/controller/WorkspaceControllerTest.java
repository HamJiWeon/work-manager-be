package work.managerbe.workspace.controller;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import work.managerbe.workspace.dto.response.WorkspaceSliceResponse;
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
import work.managerbe.global.exception.workspace.WorkspaceErrorCode;
import work.managerbe.global.exception.workspace.WorkspaceException;
import work.managerbe.workspace.dto.request.WorkspaceCreateRequest;
import work.managerbe.workspace.dto.request.WorkspaceUpdateRequest;
import org.junit.jupiter.params.provider.CsvSource;
import work.managerbe.workspace.dto.response.WorkspaceResponse;
import work.managerbe.workspace.service.WorkspaceService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 워크스페이스 생성 경로와 요청 전달, 성공 응답 및 공통 예외 응답을 검증한다.
 */
@WebMvcTest(WorkspaceController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkspaceControllerTest {

    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final String PROJECT_CODE = "WORK";
    private static final Long WORKSPACE_ID = 5L;

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

    @Test
    void 단건_조회에_성공하면_200과_워크스페이스를_반환한다() throws Exception {
        // given
        authenticate(CREATOR_ID);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 14, 9, 0);
        WorkspaceResponse response = new WorkspaceResponse(
                WORKSPACE_ID, 1L, "프로젝트 개발 가이드", "# 개발 가이드", createdAt, createdAt);
        when(workspaceService.get(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, CREATOR_ID))
                .thenReturn(response);

        // when / then
        mockMvc.perform(get("/{userId}/{code}/workspaces/{workspaceId}",
                        CREATOR_ID, PROJECT_CODE, WORKSPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(WORKSPACE_ID))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.title").value("프로젝트 개발 가이드"))
                .andExpect(jsonPath("$.content").value("# 개발 가이드"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-14T09:00:00"));
        verify(workspaceService).get(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, CREATOR_ID);
    }

    @Test
    void 단건_조회_대상이_없으면_404를_반환한다() throws Exception {
        // given
        authenticate(CREATOR_ID);
        when(workspaceService.get(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, CREATOR_ID))
                .thenThrow(WorkspaceException.of(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));

        // when / then
        mockMvc.perform(get("/{userId}/{code}/workspaces/{workspaceId}",
                        CREATOR_ID, PROJECT_CODE, WORKSPACE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
        verify(workspaceService).get(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, CREATOR_ID);
    }

    private static void authenticate(UUID requesterId) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(requesterId, null));
        SecurityContextHolder.setContext(context);
    }

    /** 기본 페이지와 명시한 페이지가 서비스에 전달되고 Slice 응답이 직렬화되는지 검증한다. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void 목록_조회는_페이지와_Slice_응답을_전달한다(int page) throws Exception {
        // given
        authenticate(CREATOR_ID);
        WorkspaceResponse item = new WorkspaceResponse(5L, 1L, "제목", "내용", null, null);
        when(workspaceService.getAll(CREATOR_ID, PROJECT_CODE, CREATOR_ID, page))
                .thenReturn(new WorkspaceSliceResponse(List.of(item), page, 10, page > 0, true));
        var request = get("/{userId}/{code}/workspaces", CREATOR_ID, PROJECT_CODE);
        if (page > 0) {
            request.param("page", Integer.toString(page));
        }

        // when / then
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.content[0].title").value("제목"))
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasPrevious").value(page > 0))
                .andExpect(jsonPath("$.hasNext").value(true));
        verify(workspaceService).getAll(CREATOR_ID, PROJECT_CODE, CREATOR_ID, page);
    }

    @Test
    void 목록_조회_페이지가_숫자가_아니면_400을_반환한다() throws Exception {
        // given
        authenticate(CREATOR_ID);

        // when / then
        mockMvc.perform(get("/{userId}/{code}/workspaces", CREATOR_ID, PROJECT_CODE)
                        .param("page", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(workspaceService);
    }


    /** 생략한 필드와 명시한 null을 포함한 PATCH 본문이 DTO로 전달되는지 검증한다. */
    @ParameterizedTest
    @CsvSource(value = {
            "'{\"title\":\"새 제목\",\"content\":\"새 내용\"}',새 제목,새 내용",
            "'{\"title\":\"새 제목\"}',새 제목,NULL",
            "'{\"content\":\"새 내용\"}',NULL,새 내용",
            "'{\"title\":null,\"content\":null}',NULL,NULL",
            "'{}',NULL,NULL",
            "'{\"title\":\"\",\"content\":\"\"}','',''"
    }, nullValues = "NULL")
    void 수정_본문을_서비스에_전달하고_200을_반환한다(String body, String title, String content) throws Exception {
        // given
        authenticate(CREATOR_ID);
        WorkspaceUpdateRequest request = new WorkspaceUpdateRequest(title, content);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 7, 9, 0);
        WorkspaceResponse response = new WorkspaceResponse(
                WORKSPACE_ID, 1L, "응답 제목", "응답 내용", updatedAt, updatedAt);
        when(workspaceService.update(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, CREATOR_ID, request))
                .thenReturn(response);

        // when / then
        mockMvc.perform(patch("/{userId}/{code}/workspaces/{workspaceId}", CREATOR_ID, PROJECT_CODE, WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(WORKSPACE_ID))
                .andExpect(jsonPath("$.title").value("응답 제목"))
                .andExpect(jsonPath("$.content").value("응답 내용"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-07T09:00:00"));
        verify(workspaceService).update(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, CREATOR_ID, request);
    }

    /** 빈 본문, JSON null 및 잘못된 JSON은 서비스 호출 전에 거부되는지 검증한다. */
    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{"})
    void 수정_본문이_유효하지_않으면_400을_반환한다(String body) throws Exception {
        // given
        authenticate(CREATOR_ID);

        // when / then
        mockMvc.perform(patch("/{userId}/{code}/workspaces/{workspaceId}", CREATOR_ID, PROJECT_CODE, WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(workspaceService);
    }

    /** 권한 예외가 수정 API에서도 403으로 변환되는지 검증한다. */
    @Test
    void 수정_권한이_없으면_403을_반환한다() throws Exception {
        // given
        UUID requesterId = UUID.randomUUID();
        authenticate(requesterId);
        WorkspaceUpdateRequest request = new WorkspaceUpdateRequest("새 제목", null);
        when(workspaceService.update(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, requesterId, request))
                .thenThrow(CommonException.of(ErrorCode.FORBIDDEN));

        // when / then
        mockMvc.perform(patch("/{userId}/{code}/workspaces/{workspaceId}", CREATOR_ID, PROJECT_CODE, WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /** 수정 대상 조회 실패가 404로 변환되는지 검증한다. */
    @Test
    void 수정_대상이_없으면_404를_반환한다() throws Exception {
        // given
        authenticate(CREATOR_ID);
        WorkspaceUpdateRequest request = new WorkspaceUpdateRequest("새 제목", null);
        when(workspaceService.update(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, CREATOR_ID, request))
                .thenThrow(WorkspaceException.of(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));

        // when / then
        mockMvc.perform(patch("/{userId}/{code}/workspaces/{workspaceId}", CREATOR_ID, PROJECT_CODE, WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    /** 경로와 인증 주체를 전달하고 본문 없는 204를 반환한다. */
    @Test
    void 삭제_성공시_본문_없는_204를_반환한다() throws Exception {
        // given
        authenticate(CREATOR_ID);

        // when / then
        mockMvc.perform(delete("/{userId}/{code}/workspaces/{workspaceId}", CREATOR_ID, PROJECT_CODE, WORKSPACE_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(workspaceService).delete(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, CREATOR_ID);
    }

    /** 삭제 권한 예외를 403 응답으로 변환한다. */
    @Test
    void 삭제_권한이_없으면_403을_반환한다() throws Exception {
        // given
        UUID requesterId = UUID.randomUUID();
        authenticate(requesterId);
        doThrow(CommonException.of(ErrorCode.FORBIDDEN)).when(workspaceService)
                .delete(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, requesterId);

        // when / then
        mockMvc.perform(delete("/{userId}/{code}/workspaces/{workspaceId}", CREATOR_ID, PROJECT_CODE, WORKSPACE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(workspaceService).delete(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, requesterId);
    }

    /** 삭제 대상 조회 실패를 404 응답으로 변환한다. */
    @Test
    void 삭제_대상이_없으면_404를_반환한다() throws Exception {
        // given
        authenticate(CREATOR_ID);
        doThrow(WorkspaceException.of(WorkspaceErrorCode.WORKSPACE_NOT_FOUND)).when(workspaceService)
                .delete(CREATOR_ID, PROJECT_CODE, WORKSPACE_ID, CREATOR_ID);

        // when / then
        mockMvc.perform(delete("/{userId}/{code}/workspaces/{workspaceId}", CREATOR_ID, PROJECT_CODE, WORKSPACE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    /** 숫자가 아닌 워크스페이스 ID는 서비스 호출 전에 거부한다. */
    @Test
    void 삭제_ID가_숫자가_아니면_400을_반환한다() throws Exception {
        // given
        authenticate(CREATOR_ID);

        // when / then
        mockMvc.perform(delete("/{userId}/{code}/workspaces/{workspaceId}", CREATOR_ID, PROJECT_CODE, "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(workspaceService);
    }

}
