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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                7L, 1L, 3L, start, end, now, now));
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
}
