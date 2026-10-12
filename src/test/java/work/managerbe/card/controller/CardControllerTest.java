package work.managerbe.card.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.card.domain.CardStatus;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.dto.request.CardFilterRequest;
import work.managerbe.card.dto.response.CardResponse;
import work.managerbe.card.dto.response.CardSliceResponse;
import work.managerbe.card.service.CardService;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import org.mockito.ArgumentCaptor;
import work.managerbe.card.dto.request.CardUpdateRequest;
import work.managerbe.global.exception.card.CardException;
import work.managerbe.global.exception.card.CardErrorCode;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 명세 경로의 인증 사용자 전달, 전체 생성 응답 및 JSON 입력 검증을 확인한다.
 */
@WebMvcTest(CardController.class)
@AutoConfigureMockMvc(addFilters = false)
class CardControllerTest {
    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private final MockMvc mvc;
    @MockitoBean CardService service;

    @Autowired
    CardControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @BeforeEach
    void 인증_설정() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(REQUESTER, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void 인증_정리() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 경로의 프로젝트 코드와 쿼리의 카드 코드를 분리해 날짜 및 담당자 필터를 전달한다.
     */
    @Test
    void 날짜와_카드_코드_필터를_함께_전달한다() throws Exception {
        // given
        var filter = CardFilterRequest.of(7L, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 15), "TEST-123");
        when(service.getAll(CREATOR, "TEST", 3L, REQUESTER, 0, 20, filter))
                .thenReturn(new CardSliceResponse(List.of(), 0, 20, false));
        // when / then
        mvc.perform(get("/{userId}/TEST/3/cards", CREATOR)
                .param("memberId", "7").param("startDate", "2026-09-11")
                .param("endDate", "2026-09-15").param("code", "TEST-123"))
                .andExpect(status().isOk());
        verify(service).getAll(CREATOR, "TEST", 3L, REQUESTER, 0, 20, filter);
    }

    /**
     * 서비스의 잘못된 기간 오류를 400 응답으로 변환한다.
     */
    @Test
    void 역전된_조회_기간은_400을_반환한다() throws Exception {
        // given
        var filter = CardFilterRequest.of(null, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 11), null);
        when(service.getAll(CREATOR, "TEST", 3L, REQUESTER, 0, 20, filter))
                .thenThrow(CardException.of(CardErrorCode.CARD_INVALID_FILTER));
        // when / then
        mvc.perform(get("/{userId}/TEST/3/cards", CREATOR)
                .param("startDate", "2026-09-15").param("endDate", "2026-09-11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CARD_INVALID_FILTER"));
    }

    /**
     * 선택한 멤버 ID를 서비스의 동적 필터로 전달한다.
     */
    @Test
    void 선택한_멤버로_카드_목록을_필터링한다() throws Exception {
        // given
        when(service.getAll(CREATOR, "TEST", 3L, REQUESTER, 0, 20, CardFilterRequest.of(7L, null, null, null)))
                .thenReturn(new CardSliceResponse(List.of(), 0, 20, false));
        // when / then
        mvc.perform(get("/{userId}/TEST/3/cards?memberId=7", CREATOR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(false));
        verify(service).getAll(CREATOR, "TEST", 3L, REQUESTER, 0, 20, CardFilterRequest.of(7L, null, null, null));
    }

    /**
     * 명시한 페이지와 카드 필드가 목록 JSON으로 반환되는지 검증한다.
     */
    @Test
    void 카드_목록은_카드_필드와_페이징_정보를_반환한다() throws Exception {
        // given
        var now = LocalDateTime.of(2026, 9, 11, 9, 0);
        var card = new CardResponse(101L, "홍길동", "로그인 API 구현", "내용", CardStatus.IN_PROGRESS,
                7L, 1L, 3L, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 15), now, now, 0, "TEST-101");
        when(service.getAll(CREATOR, "TEST", 3L, REQUESTER, 1, 2, CardFilterRequest.empty()))
                .thenReturn(new CardSliceResponse(List.of(card), 1, 2, false));
        // when / then
        mvc.perform(get("/{userId}/TEST/3/cards?page=1&size=2", CREATOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(101))
                .andExpect(jsonPath("$.items[0].code").value("TEST-101"))
                .andExpect(jsonPath("$.items[0].username").value("홍길동"))
                .andExpect(jsonPath("$.items[0].title").value("로그인 API 구현"))
                .andExpect(jsonPath("$.items[0].content").value("내용"))
                .andExpect(jsonPath("$.items[0].memberId").value(7))
                .andExpect(jsonPath("$.items[0].projectId").value(1))
                .andExpect(jsonPath("$.items[0].boardId").value(3))
                .andExpect(jsonPath("$.items[0].startDate").value("2026-09-11"))
                .andExpect(jsonPath("$.items[0].endDate").value("2026-09-15"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-09-11T09:00:00"))
                .andExpect(jsonPath("$.items[0].updatedAt").value("2026-09-11T09:00:00"))
                .andExpect(jsonPath("$.items[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[0].sortOrder").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist());
        verify(service).getAll(CREATOR, "TEST", 3L, REQUESTER, 1, 2, CardFilterRequest.empty());
    }

    /**
     * 기본 페이징 값과 빈 목록의 명세 응답을 검증한다.
     */
    @Test
    void 카드_목록이_없으면_빈_페이지를_반환한다() throws Exception {
        // given
        when(service.getAll(CREATOR, "TEST", 3L, REQUESTER, 0, 20, CardFilterRequest.empty()))
                .thenReturn(new CardSliceResponse(List.of(), 0, 20, false));
        // when / then
        mvc.perform(get("/{userId}/TEST/3/cards", CREATOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false));
        verify(service).getAll(CREATOR, "TEST", 3L, REQUESTER, 0, 20, CardFilterRequest.empty());
    }

    /**
     * 잘못된 페이지 파라미터를 서비스 호출 전에 거절한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "memberId=0", "startDate=invalid", "endDate=2026-02-30"})
    void 잘못된_목록_페이지는_400을_반환한다(String query) throws Exception {
        // given / when / then
        mvc.perform(get("/{userId}/TEST/3/cards?" + query, CREATOR))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 카드_생성은_201과_전체_필드를_반환한다() throws Exception {
        // given
        var start = LocalDate.of(2026, 9, 11);
        var end = LocalDate.of(2026, 9, 15);
        var now = LocalDateTime.of(2026, 9, 11, 9, 0);
        var request = new CardCreateRequest("홍길동", "로그인 API 구현", "로그인 요청 및 응답을 구현한다.",
                CardStatus.NOT_STARTED, 7L, 1L, 3L, start, end);
        when(service.create(CREATOR, "TEST", 3L, REQUESTER, request)).thenReturn(new CardResponse(
                101L, request.username(), request.title(), request.content(), request.status(),
                7L, 1L, 3L, start, end, now, now, 0, "TEST-101"));
        // when / then
        mvc.perform(post("/{userId}/TEST/3/cards", CREATOR).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"홍길동","title":"로그인 API 구현","content":"로그인 요청 및 응답을 구현한다.",
                         "status":"NOT_STARTED","memberId":7,"projectId":1,"boardId":3,
                         "startDate":"2026-09-11","endDate":"2026-09-15"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.username").value(request.username()))
                .andExpect(jsonPath("$.title").value(request.title()))
                .andExpect(jsonPath("$.content").value(request.content()))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.memberId").value(7))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.boardId").value(3))
                .andExpect(jsonPath("$.startDate").value("2026-09-11"))
                .andExpect(jsonPath("$.endDate").value("2026-09-15"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-11T09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-11T09:00:00"));
        verify(service).create(CREATOR, "TEST", 3L, REQUESTER, request);
    }

    /**
     * 조회 응답의 전체 필드와 인증된 요청자의 서비스 전달을 검증한다.
     */
    @Test
    void 카드_단건_조회는_200과_전체_필드를_반환한다() throws Exception {
        // given
        var start = LocalDate.of(2026, 9, 11);
        var end = LocalDate.of(2026, 9, 15);
        var now = LocalDateTime.of(2026, 9, 11, 9, 0);
        when(service.get(CREATOR, "TEST", 3L, 101L, REQUESTER)).thenReturn(new CardResponse(
                101L, "홍길동", "로그인 API 구현", "로그인 요청 및 응답을 구현한다.",
                CardStatus.NOT_STARTED, 7L, 1L, 3L, start, end, now, now, 0, "TEST-101"));
        // when / then
        mvc.perform(get("/{userId}/TEST/3/cards/101", CREATOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.username").value("홍길동"))
                .andExpect(jsonPath("$.title").value("로그인 API 구현"))
                .andExpect(jsonPath("$.content").value("로그인 요청 및 응답을 구현한다."))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.memberId").value(7))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.boardId").value(3))
                .andExpect(jsonPath("$.startDate").value("2026-09-11"))
                .andExpect(jsonPath("$.endDate").value("2026-09-15"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-11T09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-11T09:00:00"));
        verify(service).get(CREATOR, "TEST", 3L, 101L, REQUESTER);
    }

    /**
     * 카드 없음 예외가 명세의 404 오류 응답으로 변환되는지 검증한다.
     */
    @Test
    void 카드가_없으면_404를_반환한다() throws Exception {
        // given
        when(service.get(CREATOR, "TEST", 3L, 101L, REQUESTER))
                .thenThrow(CardException.of(CardErrorCode.CARD_NOT_FOUND));
        // when / then
        mvc.perform(get("/{userId}/TEST/3/cards/101", CREATOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("카드를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "", "{\"status\":\"UNKNOWN\"}",
            "{\"username\":\"홍길동\",\"title\":\" \",\"content\":\"\",\"status\":\"DONE\",\"memberId\":7,\"projectId\":1,\"boardId\":3}",
            "{\"username\":\"홍길동\",\"title\":\"제목\",\"content\":\"\",\"status\":\"DONE\",\"memberId\":0,\"projectId\":1,\"boardId\":3}"})
    void 잘못된_본문은_서비스_호출_없이_400을_반환한다(String body) throws Exception {
        // given / when / then
        mvc.perform(post("/{userId}/TEST/3/cards", CREATOR).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test
    void 수정_API는_상태와_위치를_전달하고_날짜_null과_생략을_구분한다() throws Exception {
        // given
        var now = LocalDateTime.of(2026, 9, 11, 9, 0);
        when(service.update(eq(CREATOR), eq("TEST"), eq(3L), eq(101L), eq(REQUESTER), any()))
                .thenReturn(new CardResponse(101L, "담당자", "제목", "내용", CardStatus.DONE,
                        7L, 1L, 3L, null, null, now, now, 2, "TEST-101"));
        // when / then
        mvc.perform(patch("/{userId}/TEST/3/cards/101", CREATOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\",\"sortOrder\":2,\"endDate\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.sortOrder").value(2));
        var request = ArgumentCaptor.forClass(CardUpdateRequest.class);
        verify(service).update(eq(CREATOR), eq("TEST"), eq(3L), eq(101L), eq(REQUESTER), request.capture());
        assertThat(request.getValue().isEndDateProvided()).isTrue();
        assertThat(request.getValue().isStartDateProvided()).isFalse();
    }

    @Test
    void 수정_API는_잘못된_상태를_거절한다() throws Exception {
        // given / when / then
        mvc.perform(patch("/{userId}/TEST/3/cards/101", CREATOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }


    /**
     * 비날짜 필드의 명시적 null을 서비스 호출 이전에 400으로 반환한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"{\"title\":null,\"status\":\"DONE\"}",
            "{\"content\":null,\"status\":\"DONE\"}", "{\"boardId\":null,\"status\":\"DONE\"}",
            "{\"sortOrder\":null,\"status\":\"DONE\"}", "{\"status\":null,\"title\":\"제목\"}"})
    void 수정_API는_비날짜_필드의_null을_거절한다(String body) throws Exception {
        // given / when / then
        mvc.perform(patch("/{userId}/TEST/3/cards/101", CREATOR)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    /**
     * 서비스의 수정 검증 오류를 공개 JSON 오류 응답으로 변환한다.
     */
    @Test
    void 잘못된_수정_요청의_오류_코드와_메시지를_반환한다() throws Exception {
        // given
        when(service.update(eq(CREATOR), eq("TEST"), eq(3L), eq(101L), eq(REQUESTER), any()))
                .thenThrow(CardException.of(CardErrorCode.CARD_INVALID_UPDATE));
        // when / then
        mvc.perform(patch("/{userId}/TEST/3/cards/101", CREATOR)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CARD_INVALID_UPDATE"))
                .andExpect(jsonPath("$.message").value("카드 수정 요청이 유효하지 않습니다."));
    }

}
