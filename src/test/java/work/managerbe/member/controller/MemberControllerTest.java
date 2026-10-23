package work.managerbe.member.controller;

import work.managerbe.member.domain.MemberRole;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import work.managerbe.global.exception.member.MemberErrorCode;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.member.dto.MemberPageResponse;
import work.managerbe.member.dto.request.MemberCreateRequest;
import work.managerbe.member.dto.request.MemberUpdateRequest;
import work.managerbe.member.service.MemberService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    /** PATCH 명세 경로와 인증 요청자, 역할 및 감사 필드가 서비스와 응답에 반영되는지 확인한다. */
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MEMBER"})
    void 역할_수정은_단건_경로에서_200과_변경된_멤버를_반환한다(String role) throws Exception {
        // given
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(TARGET, null, List.of()));
        SecurityContextHolder.setContext(context);
        var joined = LocalDateTime.of(2026, 9, 14, 9, 0);
        var updated = joined.plusHours(5);
        var request = new MemberUpdateRequest(MemberRole.valueOf(role));
        when(service.update(CREATOR, "TEST", 7L, TARGET, request)).thenReturn(
                new MemberResponse(7L, TARGET, 1L, request.role(), joined, null, joined, updated));
        // when / then
        mvc.perform(patch("/{userId}/TEST/7", CREATOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.userId").value(TARGET.toString()))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.joinedAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.leftAt").isEmpty())
                .andExpect(jsonPath("$.createdAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-14T14:00:00"));
        verify(service).update(CREATOR, "TEST", 7L, TARGET, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null", "{}", "{\"role\":null}", "{\"role\":\"OWNER\"}",
            "{\"role\":\"UNKNOWN\"}"})
    void 잘못된_수정_본문은_서비스_호출_없이_400을_반환한다(String body) throws Exception {
        // given / when / then
        mvc.perform(patch("/{userId}/TEST/7", CREATOR).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 멤버_삭제는_members_경로에서_204와_빈_본문을_반환한다() throws Exception {
        // given / when / then
        mvc.perform(delete("/{userId}/TEST/members/7", CREATOR))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(CREATOR, "TEST", 7L, CREATOR);
    }

    @Test
    void OWNER가_아닌_요청자의_수정과_삭제는_403을_반환한다() throws Exception {
        // given
        when(service.update(eq(CREATOR), eq("TEST"), eq(7L), eq(CREATOR), any()))
                .thenThrow(new AccessDeniedException("OWNER 권한 필요"));
        doThrow(new AccessDeniedException("OWNER 권한 필요"))
                .when(service).delete(CREATOR, "TEST", 7L, CREATOR);
        // when / then
        mvc.perform(patch("/{userId}/TEST/7", CREATOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(delete("/{userId}/TEST/members/7", CREATOR))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 없는_대상의_수정과_삭제는_404를_반환한다() throws Exception {
        // given
        when(service.update(eq(CREATOR), eq("TEST"), eq(7L), eq(CREATOR), any()))
                .thenThrow(MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND));
        doThrow(MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND))
                .when(service).delete(CREATOR, "TEST", 7L, CREATOR);
        // when / then
        mvc.perform(patch("/{userId}/TEST/7", CREATOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        mvc.perform(delete("/{userId}/TEST/members/7", CREATOR))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void OWNER_대상의_수정과_삭제는_409를_반환한다() throws Exception {
        // given
        when(service.update(eq(CREATOR), eq("TEST"), eq(7L), eq(CREATOR), any()))
                .thenThrow(MemberException.of(MemberErrorCode.MEMBER_OWNER_PROTECTED));
        doThrow(MemberException.of(MemberErrorCode.MEMBER_OWNER_PROTECTED))
                .when(service).delete(CREATOR, "TEST", 7L, CREATOR);
        // when / then
        mvc.perform(patch("/{userId}/TEST/7", CREATOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBER_OWNER_PROTECTED"));
        mvc.perform(delete("/{userId}/TEST/members/7", CREATOR))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBER_OWNER_PROTECTED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void 수정과_삭제의_잘못된_멤버_ID는_400을_반환한다(String memberId) throws Exception {
        // given / when / then
        mvc.perform(patch("/{userId}/TEST/{memberId}", CREATOR, memberId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/{userId}/TEST/members/{memberId}", CREATOR, memberId))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    /** 목록의 명세 경로가 단건 경로와 구분되고 기본 페이지 및 전체 집계를 반환하는지 확인한다. */
    @Test
    void 멤버_목록은_기본_페이지와_명세의_응답_필드를_반환한다() throws Exception {
        // given
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(TARGET, null, List.of()));
        SecurityContextHolder.setContext(context);
        var now = LocalDateTime.of(2026, 9, 14, 9, 0);
        var member = new MemberResponse(7L, TARGET, 1L, MemberRole.MEMBER, now, null, now, now);
        when(service.getAll(CREATOR, "TEST", TARGET, 0, 20))
                .thenReturn(new MemberPageResponse(List.of(member), 0, 20, 1, 1));
        // when / then
        mvc.perform(get("/{userId}/TEST/members", CREATOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].userId").value(TARGET.toString()))
                .andExpect(jsonPath("$.items[0].projectId").value(1))
                .andExpect(jsonPath("$.items[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.items[0].joinedAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.items[0].leftAt").isEmpty())
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.items[0].updatedAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        verify(service).getAll(CREATOR, "TEST", TARGET, 0, 20);
        verifyNoMoreInteractions(service);
    }

    @Test
    void 멤버_목록의_페이지_조건과_빈_목록을_전달한다() throws Exception {
        // given
        when(service.getAll(CREATOR, "TEST", CREATOR, 2, 100))
                .thenReturn(new MemberPageResponse(List.of(), 2, 100, 1, 1));
        // when / then
        mvc.perform(get("/{userId}/TEST/members", CREATOR).param("page", "2").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        verify(service).getAll(CREATOR, "TEST", CREATOR, 2, 100);
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,abc", "page,2147483648", "size,0", "size,-1", "size,101", "size,abc"})
    void 잘못된_목록_페이지_조건은_서비스_호출_없이_400을_반환한다(String name, String value) throws Exception {
        // given / when / then
        mvc.perform(get("/{userId}/TEST/members", CREATOR).param(name, value))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 목록_조회_권한이_없으면_403을_반환한다() throws Exception {
        // given
        when(service.getAll(CREATOR, "TEST", CREATOR, 0, 20)).thenThrow(new AccessDeniedException("권한 없음"));
        // when / then
        mvc.perform(get("/{userId}/TEST/members", CREATOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 목록의_프로젝트가_없으면_404를_반환한다() throws Exception {
        // given
        when(service.getAll(CREATOR, "TEST", CREATOR, 0, 20))
                .thenThrow(ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
        // when / then
        mvc.perform(get("/{userId}/TEST/members", CREATOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    /** 생성자와 다른 인증 요청자를 전달하고 명세의 단건 경로와 응답 필드를 검증한다. */
    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "ADMIN", "MEMBER"})
    void 멤버_단건_조회는_인증된_요청자를_전달하고_200과_멤버_정보를_반환한다(String role) throws Exception {
        // given
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(TARGET, null, List.of()));
        SecurityContextHolder.setContext(context);
        var now = LocalDateTime.of(2026, 9, 14, 9, 0);
        when(service.get(CREATOR, "TEST", 7L, TARGET)).thenReturn(
                new MemberResponse(7L, TARGET, 1L, MemberRole.valueOf(role), now, null, now, now));
        // when / then
        mvc.perform(get("/{userId}/{code}/{memberId}", CREATOR, "TEST", 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.userId").value(TARGET.toString()))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.joinedAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.leftAt").isEmpty())
                .andExpect(jsonPath("$.createdAt").value("2026-09-14T09:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-14T09:00:00"));
        verify(service).get(CREATOR, "TEST", 7L, TARGET);
    }

    @Test
    void 없는_멤버_조회는_404와_멤버_오류_코드를_반환한다() throws Exception {
        // given
        when(service.get(CREATOR, "TEST", 7L, CREATOR))
                .thenThrow(MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND));
        // when / then
        mvc.perform(get("/{userId}/TEST/7", CREATOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 없는_프로젝트의_멤버_조회는_404를_반환한다() throws Exception {
        // given
        when(service.get(CREATOR, "MISSING", 7L, CREATOR))
                .thenThrow(ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
        // when / then
        mvc.perform(get("/{userId}/MISSING/7", CREATOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void 조회_권한이_없으면_403을_반환한다() throws Exception {
        // given
        when(service.get(CREATOR, "TEST", 7L, CREATOR)).thenThrow(new AccessDeniedException("권한 없음"));
        // when / then
        mvc.perform(get("/{userId}/TEST/7", CREATOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "9223372036854775808"})
    void 잘못된_멤버_ID_형식은_서비스_호출_없이_400을_반환한다(String memberId) throws Exception {
        // given / when / then
        mvc.perform(get("/{userId}/TEST/{memberId}", CREATOR, memberId))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 조회_경로의_사용자_ID가_UUID가_아니면_400을_반환한다() throws Exception {
        // given / when / then
        mvc.perform(get("/invalid/TEST/7")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"role\":\"MEMBER\""})
    void 멤버_추가는_역할_기본값과_201_응답을_제공한다(String roleField) throws Exception {
        // given
        var now = LocalDateTime.of(2026, 9, 14, 9, 0);
        var request = new MemberCreateRequest(TARGET, MemberRole.MEMBER);
        when(service.create(CREATOR, "TEST", CREATOR, request)).thenReturn(
                new MemberResponse(7L, TARGET, 1L, MemberRole.MEMBER, now, null, now, now));
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
            "{\"userId\":null}",
            "{\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"role\":\"UNKNOWN\"}",
            "{\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"role\":\"ADMIN\"}", "{\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"role\":\"OWNER\"}"})
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
