package work.managerbe.member.controller;

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
import work.managerbe.global.exception.member.MemberErrorCode;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.member.dto.request.MemberCreateRequest;
import work.managerbe.member.service.MemberService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 명세 경로, 역할 기본값, 인증 사용자 전달 및 공개 오류 응답을 검증한다. */
@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerTest {
    private static final UUID CREATOR = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private final MockMvc mvc;
    @MockitoBean MemberService service;

    @Autowired
    MemberControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @BeforeEach
    void 인증_설정() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(CREATOR, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void 인증_정리() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"role\":\"MEMBER\""})
    void 멤버_추가는_역할_기본값과_201_응답을_제공한다(String roleField) throws Exception {
        // given
        var now = LocalDateTime.of(2026, 9, 14, 9, 0);
        var request = new MemberCreateRequest(TARGET, "MEMBER");
        when(service.create(CREATOR, "TEST", CREATOR, request)).thenReturn(
                new MemberResponse(7L, TARGET, 1L, "MEMBER", now, null, now, now));
        // when / then
        mvc.perform(post("/{userId}/TEST/members", CREATOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + TARGET + "\"" + roleField + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.userId").value(TARGET.toString()))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.joinedAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.leftAt").isEmpty())
                .andExpect(jsonPath("$.createdAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-14T09:00:00"));
        verify(service).create(CREATOR, "TEST", CREATOR, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "", "{\"userId\":\"invalid\"}",
            "{\"userId\":null}", "{\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"role\":\"OWNER\"}"})
    void 잘못된_본문은_서비스_호출_없이_400을_반환한다(String body) throws Exception {
        // given / when / then
        mvc.perform(post("/{userId}/TEST/members", CREATOR).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 중복_가입은_409와_멤버_오류_코드를_반환한다() throws Exception {
        // given
        when(service.create(eq(CREATOR), eq("TEST"), eq(CREATOR), any()))
                .thenThrow(MemberException.of(MemberErrorCode.MEMBER_ALREADY_EXISTS));
        // when / then
        mvc.perform(post("/{userId}/TEST/members", CREATOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + TARGET + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 프로젝트에 가입한 사용자입니다."));
    }
}
