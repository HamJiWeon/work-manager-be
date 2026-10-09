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
import work.managerbe.card.dto.response.CardResponse;
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
                7L, 1L, 3L, start, end, now, now, 0));
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
                CardStatus.NOT_STARTED, 7L, 1L, 3L, start, end, now, now, 0));
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
                        7L, 1L, 3L, null, null, now, now, 2));
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


    /** 비날짜 필드의 명시적 null을 서비스 호출 이전에 400으로 반환한다. */
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

    /** 서비스의 수정 검증 오류를 공개 JSON 오류 응답으로 변환한다. */
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
