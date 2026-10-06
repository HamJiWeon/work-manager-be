package work.managerbe.member.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.global.exception.member.*;
import work.managerbe.member.dto.*;
import work.managerbe.member.service.MemberService;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 명세의 경로와 상태 코드, 페이지 응답, 본문 검증 및 principal 전달을 검증한다. */
@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
class MemberControllerTest {
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID REQUESTER_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 7L;
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 14, 9, 0);
    private final MockMvc mvc;
    @MockitoBean MemberService service;

    @Autowired
    MemberControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @BeforeEach
    void 인증_요청자를_설정한다() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(REQUESTER_ID, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void 인증을_정리한다() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/{userId}/{projectId}/members", "/users/{userId}/projects/{projectId}/members"})
    void 추가는_기본_역할과_201_응답을_반환한다(String path) throws Exception {
        // given
        var request = new MemberCreateRequest(TARGET_ID, null);
        when(service.create(CREATOR_ID, PROJECT_ID, REQUESTER_ID, request)).thenReturn(response("MEMBER"));
        // when / then
        mvc.perform(post(path, CREATOR_ID, PROJECT_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + TARGET_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(MEMBER_ID))
                .andExpect(jsonPath("$.userId").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.joinedAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.leftAt").isEmpty());
        verify(service).create(CREATOR_ID, PROJECT_ID, REQUESTER_ID, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/{userId}/{projectId}/{memberId}", "/users/{userId}/projects/{projectId}/members/{memberId}"})
    void 단건_조회는_200_응답을_반환한다(String path) throws Exception {
        // given
        when(service.get(CREATOR_ID, PROJECT_ID, REQUESTER_ID, MEMBER_ID)).thenReturn(response("MEMBER"));
        // when / then
        mvc.perform(get(path, CREATOR_ID, PROJECT_ID, MEMBER_ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEMBER_ID));
        verify(service).get(CREATOR_ID, PROJECT_ID, REQUESTER_ID, MEMBER_ID);
    }

    @Test
    void 목록은_기본_페이지와_전체_개수를_반환한다() throws Exception {
        // given
        when(service.getAll(CREATOR_ID, PROJECT_ID, REQUESTER_ID, 0, 20))
                .thenReturn(new MemberPageResponse(List.of(response("MEMBER")), 0, 20, 1, 1));
        // when / then
        mvc.perform(get("/{userId}/{projectId}/members", CREATOR_ID, PROJECT_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(MEMBER_ID))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/{userId}/{projectId}/{memberId}", "/users/{userId}/projects/{projectId}/members/{memberId}"})
    void 역할_수정은_200을_반환한다(String path) throws Exception {
        // given
        var request = new MemberUpdateRequest("ADMIN");
        when(service.update(CREATOR_ID, PROJECT_ID, REQUESTER_ID, MEMBER_ID, request)).thenReturn(response("ADMIN"));
        // when / then
        mvc.perform(patch(path, CREATOR_ID, PROJECT_ID, MEMBER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        verify(service).update(CREATOR_ID, PROJECT_ID, REQUESTER_ID, MEMBER_ID, request);
    }

    @Test
    void 삭제는_본문_없는_204를_반환한다() throws Exception {
        // given / when / then
        mvc.perform(delete("/users/{userId}/projects/{projectId}/members/{memberId}", CREATOR_ID, PROJECT_ID, MEMBER_ID))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(CREATOR_ID, PROJECT_ID, REQUESTER_ID, MEMBER_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"userId\":null}", "{\"userId\":\"invalid\"}", "{"})
    void 잘못된_추가_본문은_400을_반환한다(String body) throws Exception {
        // given / when / then
        mvc.perform(post("/{userId}/{projectId}/members", CREATOR_ID, PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"role\":null}", "{\"role\":\" \"}"})
    void 역할_없는_수정은_400을_반환한다(String body) throws Exception {
        // given / when / then
        mvc.perform(patch("/{userId}/{projectId}/{memberId}", CREATOR_ID, PROJECT_ID, MEMBER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101", "abc,20", "0,abc"})
    void 잘못된_페이지는_400을_반환한다(String page, String size) throws Exception {
        // given / when / then
        mvc.perform(get("/{userId}/{projectId}/members", CREATOR_ID, PROJECT_ID).param("page", page).param("size", size))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 탈퇴한_멤버_조회는_404와_오류코드를_반환한다() throws Exception {
        // given
        when(service.get(CREATOR_ID, PROJECT_ID, REQUESTER_ID, MEMBER_ID))
                .thenThrow(MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND));
        // when / then
        mvc.perform(get("/{userId}/{projectId}/{memberId}", CREATOR_ID, PROJECT_ID, MEMBER_ID))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    /** 고정 응답을 사용하여 컨트롤러의 직렬화만 검증한다. */
    private static MemberResponse response(String role) {
        return new MemberResponse(MEMBER_ID, TARGET_ID, PROJECT_ID, role, TIME, null, TIME, TIME);
    }
}
