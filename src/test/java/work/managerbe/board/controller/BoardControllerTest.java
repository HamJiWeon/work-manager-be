package work.managerbe.board.controller;

import java.time.LocalDateTime;
import work.managerbe.board.dto.request.BoardUpdateItem;
import work.managerbe.board.dto.request.BoardUpdateRequest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import java.util.List;
import work.managerbe.board.dto.response.BoardSliceResponse;
import org.junit.jupiter.params.provider.CsvSource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.board.dto.request.BoardCreateRequest;
import work.managerbe.board.dto.response.BoardResponse;
import work.managerbe.board.service.BoardService;
import work.managerbe.global.exception.board.BoardErrorCode;
import work.managerbe.global.exception.board.BoardException;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 보드 생성 경로의 프로젝트 코드 전달과 JSON 검증, 성공·실패 응답을 검증한다.
 */
@WebMvcTest(BoardController.class)
@AutoConfigureMockMvc(addFilters = false)
class BoardControllerTest {
    private final MockMvc mvc;
    @MockitoBean BoardService service;
    private static final UUID REQUESTER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PROJECT_CODE = "TEST_ABC";

    /**
     * 보드 순서 한도 예외가 공통 오류 처리기를 통해 409 응답으로 변환되는지 검증한다.
     */
    @Test
    void 보드_정렬_순서_한도에_도달하면_409를_반환한다() throws Exception {
        // given
        when(service.create(eq(USER_ID), eq(PROJECT_CODE), eq(REQUESTER_ID), any()))
                .thenThrow(BoardException.of(BoardErrorCode.BOARD_SORT_ORDER_EXHAUSTED));

        // when / then
        mvc.perform(post("/{userId}/{code}/boards", USER_ID, PROJECT_CODE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"보드\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOARD_SORT_ORDER_EXHAUSTED"));
    }

    @Autowired
    BoardControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void 정상_요청은_201과_전체_응답을_반환한다() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 14, 9, 0);
        when(service.create(USER_ID, PROJECT_CODE, REQUESTER_ID, new BoardCreateRequest("Board API")))
                .thenReturn(new BoardResponse(2L, 1L, "Board API", 1, now, now));
        // when & then
        mvc.perform(post("/{userId}/{code}/boards", USER_ID, PROJECT_CODE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Board API\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.name").value("Board API"))
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andExpect(jsonPath("$.createdAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-14T09:00:00"));
        verify(service).create(USER_ID, PROJECT_CODE, REQUESTER_ID, new BoardCreateRequest("Board API"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\" \"}", "{", ""})
    void 잘못된_본문은_400을_반환한다(String body) throws Exception {
        // given & when & then
        mvc.perform(post("/{userId}/{code}/boards", USER_ID, PROJECT_CODE).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 없는_프로젝트는_404를_반환한다() throws Exception {
        // given
        when(service.create(eq(USER_ID), eq(PROJECT_CODE), eq(REQUESTER_ID), any()))
                .thenThrow(ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
        // when & then
        mvc.perform(post("/{userId}/{code}/boards", USER_ID, PROJECT_CODE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"보드\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    /**
     * 활성 멤버 검증 실패가 공통 오류 응답의 403으로 변환되는지 확인한다.
     */
    @Test
    void 프로젝트_참여_권한이_없으면_403을_반환한다() throws Exception {
        // given
        when(service.create(eq(USER_ID), eq(PROJECT_CODE), eq(REQUESTER_ID), any()))
                .thenThrow(new AccessDeniedException("활성 멤버가 아닙니다."));

        // when / then
        mvc.perform(post("/{userId}/{code}/boards", USER_ID, PROJECT_CODE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"보드\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * 기본 페이지 값과 보드 목록, 다음 데이터 존재 여부를 반환하는지 검증한다.
     */
    @Test
    void 목록_조회는_기본_페이지와_보드_정보를_반환한다() throws Exception {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 14, 9, 0);
        var board = new BoardResponse(2L, 1L, "Board API", 1, now, now);
        when(service.getAll(USER_ID, PROJECT_CODE, REQUESTER_ID, 0, 20))
                .thenReturn(new BoardSliceResponse(List.of(board), 0, 20, true));

        // when / then
        mvc.perform(get("/{userId}/{code}/boards", USER_ID, PROJECT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(2))
                .andExpect(jsonPath("$.items[0].projectId").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Board API"))
                .andExpect(jsonPath("$.items[0].sortOrder").value(1))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.items[0].updatedAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist());
    }

    @Test
    void 목록_조회는_요청한_페이지와_크기를_전달한다() throws Exception {
        // given
        when(service.getAll(USER_ID, PROJECT_CODE, REQUESTER_ID, 2, 5))
                .thenReturn(new BoardSliceResponse(List.of(), 2, 5, false));

        // when / then
        mvc.perform(get("/{userId}/{code}/boards", USER_ID, PROJECT_CODE)
                        .param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.hasNext").value(false));
        verify(service).getAll(USER_ID, PROJECT_CODE, REQUESTER_ID, 2, 5);
    }

    /**
     * 최대 조회 크기는 허용하고 서비스에 그대로 전달한다.
     */
    @Test
    void 최대_크기_100개_요청은_허용한다() throws Exception {
        // given
        when(service.getAll(USER_ID, PROJECT_CODE, REQUESTER_ID, 0, 100))
                .thenReturn(new BoardSliceResponse(List.of(), 0, 100, false));

        // when / then
        mvc.perform(get("/{userId}/{code}/boards", USER_ID, PROJECT_CODE)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        verify(service).getAll(USER_ID, PROJECT_CODE, REQUESTER_ID, 0, 100);
    }

    /**
     * 상한 초과를 포함한 잘못된 페이지 입력은 서비스 호출 전에 거절한다.
     */
    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "abc,20", "0,abc", "0,101", "0,2147483647"})
    void 잘못된_목록_페이지는_400을_반환한다(String page, String size) throws Exception {
        // given / when / then
        mvc.perform(get("/{userId}/{code}/boards", USER_ID, PROJECT_CODE)
                        .param("page", page).param("size", size))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 목록_조회_권한이_없으면_403을_반환한다() throws Exception {
        // given
        when(service.getAll(USER_ID, PROJECT_CODE, REQUESTER_ID, 0, 20))
                .thenThrow(new AccessDeniedException("활성 멤버가 아닙니다."));

        // when / then
        mvc.perform(get("/{userId}/{code}/boards", USER_ID, PROJECT_CODE))
                .andExpect(status().isForbidden());
    }

    @Test
    void 목록_조회_프로젝트가_없으면_404를_반환한다() throws Exception {
        // given
        when(service.getAll(USER_ID, PROJECT_CODE, REQUESTER_ID, 0, 20))
                .thenThrow(ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

        // when / then
        mvc.perform(get("/{userId}/{code}/boards", USER_ID, PROJECT_CODE))
                .andExpect(status().isNotFound());
    }

    /**
     * 최종 배열 순서와 이름 변경을 한 요청으로 전달하고 변경된 전체 목록을 반환한다.
     */
    @Test
    void 보드_일괄_수정은_200과_최종_목록을_반환한다() throws Exception {
        // given
        var now = LocalDateTime.of(2026, 9, 14, 14, 0);
        var request = new BoardUpdateRequest(List.of(
                new BoardUpdateItem(2L, "기획"),
                new BoardUpdateItem(1L, null)));
        when(service.update(USER_ID, PROJECT_CODE, REQUESTER_ID, request))
                .thenReturn(List.of(
                        new BoardResponse(2L, 1L, "기획", 0, now, now),
                        new BoardResponse(1L, 1L, "개발", 1, now, now)));
        // when / then
        mvc.perform(patch("/{userId}/{code}/boards", USER_ID, PROJECT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boards\":[{\"boardId\":2,\"name\":\"기획\"},{\"boardId\":1,\"name\":null}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].name").value("기획"))
                .andExpect(jsonPath("$[0].sortOrder").value(0))
                .andExpect(jsonPath("$[1].id").value(1))
                .andExpect(jsonPath("$[1].sortOrder").value(1));
        verify(service).update(USER_ID, PROJECT_CODE, REQUESTER_ID, request);
    }

    @Test
    void 최신_목록과_다른_수정_요청은_409로_반환한다() throws Exception {
        // given
        when(service.update(eq(USER_ID), eq(PROJECT_CODE), eq(REQUESTER_ID), any()))
                .thenThrow(BoardException.of(BoardErrorCode.BOARD_UPDATE_CONFLICT));
        // when / then
        mvc.perform(patch("/{userId}/{code}/boards", USER_ID, PROJECT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boards\":[{\"boardId\":1,\"name\":null}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOARD_UPDATE_CONFLICT"));
    }

    /**
     * 경로의 생성자와 다른 UUID principal을 사용해 요청자가 별도로 전달되는지 검증한다.
     */
    @org.junit.jupiter.api.BeforeEach
    void 요청자_인증_설정() {
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new TestPrincipal(REQUESTER_ID), null, List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
    }

    private record TestPrincipal(UUID userId) {
        public UUID getUserId() {
            return userId;
        }
    }

    @org.junit.jupiter.api.AfterEach
    void 인증_정리() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
