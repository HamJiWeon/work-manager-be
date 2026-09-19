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
import work.managerbe.project.dto.response.ProjectPageResponse;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.service.ProjectService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

            // when & then
            mockMvc.perform(post("/{userId}/projects", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

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
            ProjectResponse project = new ProjectResponse(
                    1L,
                    "WORK",
                    "업무 관리",
                    1L,
                    "프로젝트 설명",
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            ProjectPageResponse response = new ProjectPageResponse(
                    List.of(project),
                    1,
                    10,
                    11L,
                    2
            );
            when(projectService.getAll(userId, 1)).thenReturn(response);

            // when / then
            mockMvc.perform(get("/{userId}/projects", userId)
                            .param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(project.id()))
                    .andExpect(jsonPath("$.content[0].code").value(project.code()))
                    .andExpect(jsonPath("$.content[0].name").value(project.name()))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.totalElements").value(11))
                    .andExpect(jsonPath("$.totalPages").value(2));

            verify(projectService).getAll(userId, 1);
        }

        @Test
        @DisplayName("음수 페이지 요청이면 400을 반환한다.")
        void 음수_페이지_요청시_400_반환() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            when(projectService.getAll(userId, -1))
                    .thenThrow(CommonException.of(ErrorCode.INVALID_REQUEST));

            // when / then
            mockMvc.perform(get("/{userId}/projects", userId)
                            .param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verify(projectService).getAll(userId, -1);
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
