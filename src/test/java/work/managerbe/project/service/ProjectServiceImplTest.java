package work.managerbe.project.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.request.ProjectCreateRequest;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.mapper.ProjectMapper;
import work.managerbe.project.repository.ProjectRepository;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import work.managerbe.member.domain.Member;
import work.managerbe.member.repository.MemberRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMapper mapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Nested
    @DisplayName("create")
    class Create {
      
        /**
         * 생성자가 없으면 프로젝트와 멤버 저장 전에 요청을 거절한다.
         */
        @Test
        void 존재하지_않는_사용자는_프로젝트를_생성할_수_없다() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request = new ProjectCreateRequest("WORK", "업무 관리", null);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when
            UserException exception = assertThrows(UserException.class,
                    () -> projectService.create(userId, request));

            // then
            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
            verifyNoInteractions(projectRepository, memberRepository, mapper);
        }

        /**
         * 사용자 ID가 없으면 조회와 저장을 수행하지 않는다.
         */
        @Test
        void 사용자_ID가_없으면_프로젝트를_생성할_수_없다() {
            // given
            ProjectCreateRequest request = new ProjectCreateRequest("WORK", "업무 관리", null);

            // when
            UserException exception = assertThrows(UserException.class,
                    () -> projectService.create(null, request));

            // then
            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
            verifyNoInteractions(userRepository, projectRepository, memberRepository, mapper);
        }

        @Test
        @DisplayName("중복이 없으면 코드를 대문자로 변환하고 생성자와 함께 저장한다.")
        void 프로젝트_생성() {
            // given
            String inputCode = "work";
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest(inputCode, "업무 관리", "프로젝트 설명");

            Project savedProject = mock(Project.class);
            User creator = User.create("생성자", "creator@example.com", null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(creator));
            ProjectResponse expectedResponse = mock(ProjectResponse.class);

            when(projectRepository.existsByCreator_IdAndCode(userId, "WORK")).thenReturn(false);
            when(projectRepository.saveAndFlush(any(Project.class)))
                    .thenReturn(savedProject);
            when(mapper.toResponse(savedProject))
                    .thenReturn(expectedResponse);
            // when
            ProjectResponse response = projectService.create(userId, request);

            // then
            ArgumentCaptor<Project> captor =
                    ArgumentCaptor.forClass(Project.class);

            verify(projectRepository).existsByCreator_IdAndCode(userId, "WORK");
            verify(projectRepository).saveAndFlush(captor.capture());

            Project project = captor.getValue();

            assertThat(project.getCode()).isEqualTo("WORK");
            assertThat(project.getCreator()).isSameAs(creator);
            verify(projectRepository, never()).findByUser_Id(any());
            assertThat(project.getName()).isEqualTo(request.name());
            assertThat(project.getDescription()).isEqualTo(request.description());
            assertThat(response).isSameAs(expectedResponse);
            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(memberCaptor.capture());
            assertThat(memberCaptor.getValue().getUser()).isSameAs(creator);
            assertThat(memberCaptor.getValue().getProject()).isSameAs(savedProject);
            assertThat(memberCaptor.getValue().getRole()).isEqualTo("OWNER");
            assertThat(memberCaptor.getValue().getLeftAt()).isNull();

            verify(mapper).toResponse(savedProject);
        }

        @Test
        @DisplayName("사용자의 기존 코드와 대소문자 구분 없이 중복되면 저장하지 않는다.")
        void 사용자_코드_중복() {
            // given
            String inputCode = "work";
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request = new ProjectCreateRequest(inputCode, "업무 관리", null);
            User creator = User.create("생성자", "creator@example.com", null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(creator));
            when(projectRepository.existsByCreator_IdAndCode(userId, "WORK")).thenReturn(true);

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_DUPLICATE_CODE);
            verify(projectRepository).existsByCreator_IdAndCode(userId, "WORK");
            verify(projectRepository, never()).saveAndFlush(any(Project.class));
            verifyNoInteractions(memberRepository, mapper);
        }

        /**
         * OWNER 저장 실패를 삼키지 않고 호출자에게 전달해 트랜잭션 롤백이 가능하도록 한다.
         */
        @Test
        void OWNER_저장이_실패하면_성공_응답을_반환하지_않는다() {
            // given
            User creator = User.create("생성자", "creator@example.com", null);
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request = new ProjectCreateRequest("WORK", "업무 관리", null);
            Project savedProject = mock(Project.class);
            IllegalStateException failure = new IllegalStateException("멤버 저장 실패");
            when(userRepository.findById(userId)).thenReturn(Optional.of(creator));
            when(projectRepository.saveAndFlush(any(Project.class))).thenReturn(savedProject);
            when(memberRepository.save(any(Member.class))).thenThrow(failure);

            // when
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> projectService.create(userId, request));

            // then
            assertThat(exception).isSameAs(failure);
            verifyNoInteractions(mapper);
        }

        /**
         * 사전 검사를 통과한 뒤 DB 유니크 제약에 걸리는 경합 경로의 예외 변환을 검증한다.
         */
        @Test
        void 저장시_코드_유니크_위반이면_중복_예외로_변환한다() {
            // given
            UUID userId = UUID.randomUUID();
            User creator = User.create("생성자", "creator@example.com", null);
            ProjectCreateRequest request = new ProjectCreateRequest("work", "업무 관리", null);
            var violation = new ConstraintViolationException("중복", new SQLException("중복", "23505"),
                    "uk_projects_user_code");
            var failure = new DataIntegrityViolationException("저장 실패", violation);
            when(userRepository.findById(userId)).thenReturn(Optional.of(creator));
            when(projectRepository.existsByCreator_IdAndCode(userId, "WORK")).thenReturn(false);
            when(projectRepository.saveAndFlush(any(Project.class))).thenThrow(failure);

            // when
            ProjectException exception = assertThrows(ProjectException.class,
                    () -> projectService.create(userId, request));

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_DUPLICATE_CODE);
            assertThat(exception.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
            verifyNoInteractions(memberRepository, mapper);
        }

        /**
         * 다른 제약 또는 이름을 알 수 없는 제약 위반은 코드 중복으로 바꾸지 않는다.
         */
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"fk_projects_user", "uk_other_constraint"})
        void 다른_제약_위반은_원래_예외를_전달한다(String constraintName) {
            // given
            UUID userId = UUID.randomUUID();
            User creator = User.create("생성자", "creator@example.com", null);
            var violation = new ConstraintViolationException("제약 위반", new SQLException("제약 위반"), constraintName);
            var failure = new DataIntegrityViolationException("저장 실패", violation);
            when(userRepository.findById(userId)).thenReturn(Optional.of(creator));
            when(projectRepository.saveAndFlush(any(Project.class))).thenThrow(failure);

            // when
            DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                    () -> projectService.create(userId, new ProjectCreateRequest("WORK", "업무 관리", null)));

            // then
            assertThat(exception).isSameAs(failure);
            verifyNoInteractions(memberRepository, mapper);
        }

        /**
         * Hibernate 제약 위반 원인이 없는 저장 오류도 원본 그대로 전달한다.
         */
        @Test
        void 제약_위반_원인이_없으면_원래_예외를_전달한다() {
            // given
            UUID userId = UUID.randomUUID();
            User creator = User.create("생성자", "creator@example.com", null);
            var failure = new DataIntegrityViolationException("저장 실패");
            when(userRepository.findById(userId)).thenReturn(Optional.of(creator));
            when(projectRepository.saveAndFlush(any(Project.class))).thenThrow(failure);

            // when
            DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                    () -> projectService.create(userId, new ProjectCreateRequest("WORK", "업무 관리", null)));

            // then
            assertThat(exception).isSameAs(failure);
            verifyNoInteractions(memberRepository, mapper);
        }

        @Test
        @DisplayName("프로젝트 코드에 밑줄이 포함되면 예외가 발생한다.")
        void 프로젝트코드_밑줄_포함() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest("WORK_TASK", "업무 관리", "프로젝트 설명");

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE_FORMAT);
            assertThat(exception.getErrorCode().getHttpStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("프로젝트 이름이 없으면 예외가 발생한다.")
        void 이름_누락() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest("WORK", null, "프로젝트 설명");

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ProjectErrorCode.PROJECT_INVALID_NAME);

            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("프로젝트 코드가 공백이면 예외가 발생한다.")
        void 프로젝트코드_공백() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest("   ", "업무 관리", "프로젝트 설명");

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE);

            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("프로젝트 코드가 null이면 예외가 발생한다.")
        void 프로젝트코드_누락() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest(null, "업무 관리", "프로젝트 설명");

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE);

            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("프로젝트 이름이 공백이면 예외가 발생한다.")
        void 이름_공백() {
            // given
            UUID userId = UUID.randomUUID();
            ProjectCreateRequest request =
                    new ProjectCreateRequest("WORK", "   ", "프로젝트 설명");

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.create(userId, request)
            );

            // then
            assertThat(exception.getErrorCode())
                    .isEqualTo(ProjectErrorCode.PROJECT_INVALID_NAME);

            verifyNoInteractions(projectRepository, mapper);
        }
    }

    @Nested
    @DisplayName("get")
    class Get {
        private final UUID requesterId = UUID.randomUUID();
        @Test
        @DisplayName("요청자가 접근 가능한 생성자와 코드의 프로젝트를 반환한다.")
        void 프로젝트_단건_조회() {
            // given
            UUID creatorId = UUID.randomUUID();
            Project project = Project.create(User.create("생성자", "creator@example.com", null), "WORK", "업무 관리", null);
            ProjectResponse expectedResponse = mock(ProjectResponse.class);
            when(projectRepository.findAccessibleProject(creatorId, "WORK", requesterId)).thenReturn(Optional.of(project));
            when(mapper.toResponse(project)).thenReturn(expectedResponse);

            // when
            ProjectResponse response = projectService.get(creatorId, "WORK", requesterId);

            // then
            assertThat(response).isSameAs(expectedResponse);
            verify(projectRepository).findAccessibleProject(creatorId, "WORK", requesterId);
            verify(mapper).toResponse(project);
            verify(projectRepository, never()).findByUser_Id(any());
        }

        @Test
        @DisplayName("프로젝트가 없거나 접근할 수 없으면 프로젝트 조회를 거절한다.")
        void 조회할_코드가_없음() {
            // given
            UUID creatorId = UUID.randomUUID();
            when(projectRepository.findAccessibleProject(creatorId, "WORK", requesterId)).thenReturn(Optional.empty());

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.get(creatorId, "WORK", requesterId)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND);
            verifyNoInteractions(mapper);
        }

        /**
         * 같은 코드라도 요청한 생성자의 프로젝트를 각각 반환한다.
         */
        @Test
        void 같은_코드의_프로젝트를_생성자별로_구분해_조회한다() {
            // given
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            Project first = Project.create(User.create("첫 생성자", "first@example.com", null), "WORK", "첫 프로젝트", null);
            Project second = Project.create(User.create("둘째 생성자", "second@example.com", null), "WORK", "둘째 프로젝트", null);
            ProjectResponse firstResponse = mock(ProjectResponse.class);
            ProjectResponse secondResponse = mock(ProjectResponse.class);
            when(projectRepository.findAccessibleProject(firstId, "WORK", requesterId)).thenReturn(Optional.of(first));
            when(projectRepository.findAccessibleProject(secondId, "WORK", requesterId)).thenReturn(Optional.of(second));
            when(mapper.toResponse(first)).thenReturn(firstResponse);
            when(mapper.toResponse(second)).thenReturn(secondResponse);

            // when / then
            assertThat(projectService.get(firstId, "WORK", requesterId)).isSameAs(firstResponse);
            assertThat(projectService.get(secondId, "WORK", requesterId)).isSameAs(secondResponse);
            verify(projectRepository, never()).findByUser_Id(any());
        }

        @Test
        @DisplayName("조회할 코드가 null이면 예외가 발생한다.")
        void 조회_코드_누락() {
            // given
            UUID creatorId = UUID.randomUUID();

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.get(creatorId, null, requesterId)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE);
            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("조회할 코드가 공백이면 예외가 발생한다.")
        void 조회_코드_공백() {
            // given
            UUID creatorId = UUID.randomUUID();

            // when
            ProjectException exception = assertThrows(
                    ProjectException.class,
                    () -> projectService.get(creatorId, "   ", requesterId)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_INVALID_CODE);
            verifyNoInteractions(projectRepository, mapper);
        }

        @Test
        @DisplayName("조회할 사용자 ID가 null이면 예외가 발생한다.")
        void 조회_사용자_누락() {
            // given
            String code = "WORK";

            // when
            UserException exception = assertThrows(
                    UserException.class,
                    () -> projectService.get(null, code, requesterId)
            );

            // then
            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
            verifyNoInteractions(projectRepository, mapper);
        }

        /**
         * 인증 요청자 ID가 없으면 권한 조회를 수행하지 않는다.
         */
        @Test
        void 조회_요청자_ID가_없으면_거절한다() {
            // given
            UUID creatorId = UUID.randomUUID();

            // when
            UserException exception = assertThrows(UserException.class,
                    () -> projectService.get(creatorId, "WORK", null));

            // then
            assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
            verifyNoInteractions(projectRepository, mapper);
        }

    }

}
