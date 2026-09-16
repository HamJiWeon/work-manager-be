package work.managerbe.project.controller;

import org.junit.jupiter.api.DisplayName;
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
        @DisplayName("접두사로 조회하면 200과 프로젝트 정보를 반환한다.")
        void 정상_조회시_200_반환() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            String code = "WORK";
            ProjectResponse response = new ProjectResponse(
                    1L, "WORK",
                    "업무 관리", 1L, "프로젝트 설명",
                    LocalDateTime.now(), LocalDateTime.now()
            );
            when(projectService.get(userId, code)).thenReturn(response);

            // when & then
            mockMvc.perform(get("/{userId}/{code}", userId, code))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(response.id()))
                    .andExpect(jsonPath("$.code").value(response.code()))
                    .andExpect(jsonPath("$.name").value(response.name()))
                    .andExpect(jsonPath("$.nextCardNumber").value(response.nextCardNumber()))
                    .andExpect(jsonPath("$.description").value(response.description()));

            verify(projectService).get(userId, code);
        }

        @Test
        @DisplayName("프로젝트가 없으면 404를 반환한다.")
        void 프로젝트_없을시_404_반환() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            String code = "WORK";
            when(projectService.get(userId, code))
                    .thenThrow(ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/{userId}/{code}", userId, code))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("프로젝트를 찾을 수 없습니다."));

            verify(projectService).get(userId, code);
        }

        @Test
        @DisplayName("사용자 ID가 UUID 형식이 아니면 400을 반환한다.")
        void 사용자_ID_형식_오류시_400_반환() throws Exception {
            // given
            String userId = "invalid-user-id";
            String code = "WORK";

            // when & then
            mockMvc.perform(get("/{userId}/{code}", userId, code))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(projectService);
        }
    }

}