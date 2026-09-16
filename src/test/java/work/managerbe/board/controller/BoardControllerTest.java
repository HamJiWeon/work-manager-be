package work.managerbe.board.controller;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.dto.BoardResponse;
import work.managerbe.board.service.BoardService;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 보드 생성 경로의 JSON 검증과 성공·실패 응답을 검증한다.
 */
@WebMvcTest(BoardController.class)
@AutoConfigureMockMvc(addFilters = false)
class BoardControllerTest {
    private final MockMvc mvc;
    @MockitoBean BoardService service;
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    BoardControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void 정상_요청은_201과_전체_응답을_반환한다() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 14, 9, 0);
        when(service.create(USER_ID, 1L, new BoardCreateRequest("Board API")))
                .thenReturn(new BoardResponse(2L, 1L, "Board API", 1, now, now));
        // when & then
        mvc.perform(post("/{userId}/1/boards", USER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Board API\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.name").value("Board API"))
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andExpect(jsonPath("$.createdAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-14T09:00:00"));
        verify(service).create(USER_ID, 1L, new BoardCreateRequest("Board API"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\" \"}", "{", ""})
    void 잘못된_본문은_400을_반환한다(String body) throws Exception {
        // given & when & then
        mvc.perform(post("/{userId}/1/boards", USER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 없는_프로젝트는_404를_반환한다() throws Exception {
        // given
        when(service.create(eq(USER_ID), eq(1L), any()))
                .thenThrow(ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
        // when & then
        mvc.perform(post("/{userId}/1/boards", USER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"보드\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }
}
