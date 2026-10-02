package work.managerbe.project.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import work.managerbe.global.exception.CommonException;
import work.managerbe.global.exception.ErrorCode;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.project.dto.request.ProjectCreateRequest;
import work.managerbe.project.dto.request.ProjectUpdateRequest;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.dto.response.ProjectSliceResponse;
import work.managerbe.project.service.ProjectService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서비스 응답과 예외를 모킹하여 프로젝트 생성 및 단건 조회 API의 요청 처리와 응답을 검증한다.
 */
@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ProjectService projectService;

    @Nested
    @DisplayName("POST /{userId}/projects")
    class CreateProject {

        @Test
        @DisplayName("정상 요청이면 201과 생성된 프로젝트를 반환한다.")
        void 정상_요청시_201_반환() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            authenticate(userId);
            ProjectCreateRequest request =
                    new ProjectCreateRequest("WORK", "업무 관리", "프로젝트 설명");

            ProjectResponse response = new ProjectResponse(
                    1L, request.code(),
                    request.name(), 1L, request.description(),
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(projectService.create(userId, request)).thenReturn(response);

            // when & then
            mockMvc.perform(post("/{userId}/projects", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(response.id()))
                    .andExpect(jsonPath("$.code").value(response.code()))
                    .andExpect(jsonPath("$.name").value(response.name()))
                    .andExpect(jsonPath("$.nextCardNumber").value(response.nextCardNumber()))
                    .andExpect(jsonPath("$.description").value(response.description()));

            verify(projectService).create(userId, request);
        }

        @Test
        @DisplayName("이름 누락으로 서비스 예외가 발생하면 400을 반환한다.")
        void 이름_누락시_400_반환() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            authenticate(userId);
            ProjectCreateRequest request =
                    new ProjectCreateRequest("WORK", null, "프로젝트 설명");

            when(projectService.create(userId, request))
                    .thenThrow(ProjectException.of(ProjectErrorCode.PROJECT_INVALID_NAME));

            // when & then
            mockMvc.perform(post("/{userId}/projects", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PROJECT_INVALID_NAME"));

            verify(projectService).create(userId, request);
        }

        @Test
        @DisplayName("요청 본문이 없으면 400을 반환한다.")
        void 본문_누락시_400_반환() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            authenticate(userId);

            // when & then
            mockMvc.perform(post("/{userId}/projects", userId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(projectService);
        }

        @Test
        @DisplayName("JSON 형식이 잘못되면 400을 반환한다.")
        void JSON_형식_오류시_400_반환() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            authenticate(userId);

            // when & then
            mockMvc.perform(post("/{userId}/projects", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(projectService);
        }

        @Test
        @DisplayName("경로의 사용자와 인증 사용자가 다르면 403을 반환한다.")
        void 다른_사용자의_경로로_프로젝트를_생성할_수_없다() throws Exception {
            // given
            UUID pathUserId = UUID.randomUUID();
            authenticate(UUID.randomUUID());
            ProjectCreateRequest request =
                    new ProjectCreateRequest("WORK", "업무 관리", "프로젝트 설명");

            // when & then
            mockMvc.perform(post("/{userId}/projects", pathUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            verifyNoInteractions(projectService);
        }
    }

    @Nested
    @DisplayName("GET /{userId}/{code}")
    class GetProject {

        @Test
        @DisplayName("URL의 생성자와 인증 principal의 요청자를 구분해 조회한다.")
        void 정상_조회시_200_반환() throws Exception {
            // given
            UUID creatorId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            authenticate(requesterId);
            String code = "WORK";
            ProjectResponse response = new ProjectResponse(
                    1L, "WORK",
                    "업무 관리", 1L, "프로젝트 설명",
                    LocalDateTime.now(), LocalDateTime.now()
            );
            when(projectService.get(creatorId, code, requesterId)).thenReturn(response);

            // when & then
            mockMvc.perform(get("/{userId}/{code}", creatorId, code))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(response.id()))
                    .andExpect(jsonPath("$.code").value(response.code()))
                    .andExpect(jsonPath("$.name").value(response.name()))
                    .andExpect(jsonPath("$.nextCardNumber").value(response.nextCardNumber()))
                    .andExpect(jsonPath("$.description").value(response.description()));

            verify(projectService).get(creatorId, code, requesterId);
        }

        @Test
        @DisplayName("프로젝트가 없으면 404를 반환한다.")
        void 프로젝트_없을시_404_반환() throws Exception {
            // given
            UUID creatorId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            authenticate(requesterId);
            String code = "WORK";
            when(projectService.get(creatorId, code, requesterId))
                    .thenThrow(ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/{userId}/{code}", creatorId, code))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("프로젝트를 찾을 수 없습니다."));

            verify(projectService).get(creatorId, code, requesterId);
        }

        @Test
        @DisplayName("사용자 ID가 UUID 형식이 아니면 400을 반환한다.")
        void 사용자_ID_형식_오류시_400_반환() throws Exception {
            // given
            String creatorId = "invalid-user-id";
            String code = "WORK";

            // when & then
            mockMvc.perform(get("/{userId}/{code}", creatorId, code))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(projectService);
        }
        /**
         * 인증 principal이 없을 때 경로의 생성자를 요청자로 대신 사용하지 않는다.
         */
        @Test
        void 인증_정보가_없으면_요청자_ID를_null로_전달한다() throws Exception {
            // given
            UUID creatorId = UUID.randomUUID();
            when(projectService.get(creatorId, "WORK", null))
                    .thenThrow(UserException.of(UserErrorCode.USER_NOT_FOUND));

            // when / then
            mockMvc.perform(get("/{userId}/{code}", creatorId, "WORK"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
            verify(projectService).get(creatorId, "WORK", null);
        }

    }

    @Nested
    @DisplayName("GET /{userId}/projects")
    class GetAllProjects {

        @Test
        @DisplayName("페이지 번호에 해당하는 프로젝트 목록과 페이지 정보를 반환한다.")
        void 프로젝트_전체_조회() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            authenticate(userId);
            ProjectResponse project = new ProjectResponse(
                    1L,
                    "WORK",
                    "업무 관리",
                    1L,
                    "프로젝트 설명",
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            ProjectSliceResponse response = new ProjectSliceResponse(List.of(project), 1, 10, true, true);
            when(projectService.getAll(userId, userId, 1)).thenReturn(response);

            // when / then
            mockMvc.perform(get("/{userId}/projects", userId)
                            .param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(project.id()))
                    .andExpect(jsonPath("$.content[0].code").value(project.code()))
                    .andExpect(jsonPath("$.content[0].name").value(project.name()))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.hasPrevious").value(true))
                    .andExpect(jsonPath("$.hasNext").value(true));

            verify(projectService).getAll(userId, userId, 1);
        }

        @Test
        @DisplayName("음수 페이지 요청이면 400을 반환한다.")
        void 음수_페이지_요청시_400_반환() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            authenticate(userId);
            when(projectService.getAll(userId, userId, -1))
                    .thenThrow(CommonException.of(ErrorCode.INVALID_REQUEST));

            // when / then
            mockMvc.perform(get("/{userId}/projects", userId)
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verify(projectService).getAll(userId, userId, -1);
        }

        @Test
        @DisplayName("경로 사용자와 인증 사용자가 다르면 403을 반환한다.")
        void 다른_사용자의_프로젝트_목록_조회시_403_반환() throws Exception {
            // given
            UUID pathUserId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            authenticate(requesterId);
            when(projectService.getAll(pathUserId, requesterId, 0))
                    .thenThrow(CommonException.of(ErrorCode.FORBIDDEN));

            // when / then
            mockMvc.perform(get("/{userId}/projects", pathUserId)
                            .param("page", "0"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            verify(projectService).getAll(pathUserId, requesterId, 0);
        }

        @Test
        @DisplayName("사용자 ID가 UUID 형식이 아니면 400을 반환한다.")
        void 사용자_ID_형식_오류시_400_반환() throws Exception {
            // when / then
            mockMvc.perform(get("/{userId}/projects", "invalid-user-id")
                            .param("page", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(projectService);
        }
    }

    @Nested
    @DisplayName("PATCH /{userId}/{code}")
    class UpdateProject {

        @Test
        @DisplayName("인증된 생성자의 이름 수정 요청이면 200과 수정된 프로젝트를 반환한다.")
        void 프로젝트_이름_수정() throws Exception {
            // given
            UUID creatorId = UUID.randomUUID();
            authenticate(creatorId);
            ProjectUpdateRequest request = new ProjectUpdateRequest("변경된 이름", null);
            ProjectResponse response = new ProjectResponse(
                    1L,
                    "WORK",
                    request.name(),
                    1L,
                    "프로젝트 설명",
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            when(projectService.update(creatorId, "WORK", creatorId, request)).thenReturn(response);

            // when / then
            mockMvc.perform(patch("/{userId}/{code}", creatorId, "WORK")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(response.id()))
                    .andExpect(jsonPath("$.code").value(response.code()))
                    .andExpect(jsonPath("$.name").value("변경된 이름"));

            verify(projectService).update(creatorId, "WORK", creatorId, request);
        }

        @Test
        @DisplayName("이름이 유효하지 않으면 서비스의 400 예외 응답을 반환한다.")
        void 유효하지_않은_이름_수정시_400_반환() throws Exception {
            // given
            UUID creatorId = UUID.randomUUID();
            authenticate(creatorId);
            ProjectUpdateRequest request = new ProjectUpdateRequest("   ", null);
            when(projectService.update(creatorId, "WORK", creatorId, request))
                    .thenThrow(ProjectException.of(ProjectErrorCode.PROJECT_INVALID_NAME));

            // when / then
            mockMvc.perform(patch("/{userId}/{code}", creatorId, "WORK")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PROJECT_INVALID_NAME"));

            verify(projectService).update(creatorId, "WORK", creatorId, request);
        }

        @Test
        @DisplayName("요청 본문이 없으면 서비스를 호출하지 않고 400을 반환한다.")
        void 본문_누락시_400_반환() throws Exception {
            // given
            UUID creatorId = UUID.randomUUID();
            authenticate(creatorId);

            // when / then
            mockMvc.perform(patch("/{userId}/{code}", creatorId, "WORK")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(projectService);
        }
    }

    @Nested
    @DisplayName("DELETE /{userId}/{code}")
    class DeleteProject {

        /**
         * 인증된 생성자의 삭제 요청을 서비스에 전달하고 본문 없는 204 응답을 반환한다.
         */
        @Test
        void 프로젝트_삭제시_204를_반환한다() throws Exception {
            // given
            UUID creatorId = UUID.randomUUID();
            authenticate(creatorId);

            // when / then
            mockMvc.perform(delete("/{userId}/{code}", creatorId, "WORK"))
                    .andExpect(status().isNoContent());

            verify(projectService).delete(creatorId, "WORK", creatorId);
        }

        /**
         * 인증 사용자가 생성자와 다르면 서비스의 권한 예외를 403 응답으로 변환한다.
         */
        @Test
        void 생성자가_아닌_사용자의_삭제시_403을_반환한다() throws Exception {
            // given
            UUID creatorId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            authenticate(requesterId);
            doThrow(CommonException.of(ErrorCode.FORBIDDEN))
                    .when(projectService).delete(creatorId, "WORK", requesterId);

            // when / then
            mockMvc.perform(delete("/{userId}/{code}", creatorId, "WORK"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            verify(projectService).delete(creatorId, "WORK", requesterId);
        }

        /**
         * 삭제할 프로젝트가 없으면 서비스의 프로젝트 예외를 404 응답으로 변환한다.
         */
        @Test
        void 존재하지_않는_프로젝트_삭제시_404를_반환한다() throws Exception {
            // given
            UUID creatorId = UUID.randomUUID();
            authenticate(creatorId);
            doThrow(ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND))
                    .when(projectService).delete(creatorId, "MISSING", creatorId);

            // when / then
            mockMvc.perform(delete("/{userId}/{code}", creatorId, "MISSING"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));

            verify(projectService).delete(creatorId, "MISSING", creatorId);
        }

        /**
         * 경로 사용자 ID가 UUID 형식이 아니면 서비스 호출 전에 400 응답을 반환한다.
         */
        @Test
        void 사용자_ID_형식이_잘못되면_400을_반환한다() throws Exception {
            // when / then
            mockMvc.perform(delete("/{userId}/{code}", "invalid-user-id", "WORK"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(projectService);
        }
    }

    /**
     * 보안 필터가 꺼진 MVC 테스트에서 UUID principal 전달만 검증하도록 인증 정보를 준비한다.
     * 실제 로그인 처리나 보안 필터의 인증 검증을 대신하지 않는다.
     */
    private static void authenticate(UUID requesterId) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(requesterId, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void 인증_정보를_정리한다() {
        SecurityContextHolder.clearContext();
    }

}
