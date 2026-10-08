package work.managerbe.workspace.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.exception.workspace.WorkspaceErrorCode;
import work.managerbe.global.exception.workspace.WorkspaceException;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;
import work.managerbe.workspace.domain.Workspace;
import work.managerbe.workspace.dto.request.WorkspaceUpdateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** 실제 서비스와 JPA를 연결하여 부분 수정 저장, 감사 시각 및 프로젝트 범위 제한을 검증한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-service-integration-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Transactional
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkspaceServiceIntegrationTest {

    private static final LocalDateTime PREVIOUS_UPDATED_AT = LocalDateTime.of(2025, 1, 1, 0, 0);

    private final WorkspaceService workspaceService;
    private final EntityManager entityManager;

    /** 저장 후 다시 조회하여 null 필드 유지와 응답 및 DB의 수정 시각 일치를 검증한다. */
    @Test
    void 부분_수정은_DB와_응답에_반영하고_null_필드는_유지한다() {
        // given
        User creator = User.create("생성자", "workspace-update@example.com", null);
        entityManager.persist(creator);
        Project project = Project.create(creator, "WORK", "프로젝트", null);
        entityManager.persist(project);
        Workspace workspace = Workspace.create(project, "기존 제목", "기존 내용");
        entityManager.persist(workspace);
        entityManager.flush();
        entityManager.createNativeQuery("update workspaces set updated_at = :updatedAt where id = :id")
                .setParameter("updatedAt", PREVIOUS_UPDATED_AT)
                .setParameter("id", workspace.getId())
                .executeUpdate();
        entityManager.clear();

        // when
        var response = workspaceService.update(creator.getId(), "WORK", workspace.getId(), creator.getId(),
                new WorkspaceUpdateRequest("새 제목", null));
        entityManager.clear();
        Workspace saved = entityManager.find(Workspace.class, workspace.getId());

        // then
        assertThat(saved.getTitle()).isEqualTo("새 제목");
        assertThat(saved.getContent()).isEqualTo("기존 내용");
        assertThat(response.title()).isEqualTo(saved.getTitle());
        assertThat(response.content()).isEqualTo(saved.getContent());
        assertThat(response.updatedAt()).isAfter(PREVIOUS_UPDATED_AT);
        assertThat(saved.getUpdatedAt()).isCloseTo(response.updatedAt(), within(1, ChronoUnit.MICROS));
        assertThat(saved.getCreatedAt()).isCloseTo(response.createdAt(), within(1, ChronoUnit.MICROS));
    }

    /** 두 필드가 모두 null인 수정은 데이터와 기존 수정 시각을 유지하는지 검증한다. */
    @Test
    void 모든_필드가_null이면_DB_값과_수정시각을_유지한다() {
        // given
        User creator = User.create("생성자", "workspace-noop@example.com", null);
        entityManager.persist(creator);
        Project project = Project.create(creator, "WORK", "프로젝트", null);
        entityManager.persist(project);
        Workspace workspace = Workspace.create(project, "기존 제목", "기존 내용");
        entityManager.persist(workspace);
        entityManager.flush();
        entityManager.clear();
        LocalDateTime originalUpdatedAt = entityManager.find(Workspace.class, workspace.getId()).getUpdatedAt();

        // when
        var response = workspaceService.update(creator.getId(), "WORK", workspace.getId(), creator.getId(),
                new WorkspaceUpdateRequest(null, null));
        entityManager.clear();
        Workspace saved = entityManager.find(Workspace.class, workspace.getId());

        // then
        assertThat(saved.getTitle()).isEqualTo("기존 제목");
        assertThat(saved.getContent()).isEqualTo("기존 내용");
        assertThat(saved.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(response.updatedAt()).isEqualTo(originalUpdatedAt);
    }

    /** 동일 생성자의 다른 프로젝트에 속한 워크스페이스도 경로가 다르면 수정되지 않는지 검증한다. */
    @Test
    void 다른_프로젝트의_워크스페이스는_수정하지_못한다() {
        // given
        User creator = User.create("생성자", "workspace-path@example.com", null);
        entityManager.persist(creator);
        Project project = Project.create(creator, "WORK", "프로젝트", null);
        Project other = Project.create(creator, "OTHER", "다른 프로젝트", null);
        entityManager.persist(project);
        entityManager.persist(other);
        Workspace workspace = Workspace.create(other, "기존 제목", "기존 내용");
        entityManager.persist(workspace);
        entityManager.flush();
        entityManager.clear();

        // when / then
        assertThatThrownBy(() -> workspaceService.update(creator.getId(), "WORK", workspace.getId(), creator.getId(),
                new WorkspaceUpdateRequest("새 제목", "새 내용")))
                .isInstanceOfSatisfying(WorkspaceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        entityManager.clear();
        Workspace saved = entityManager.find(Workspace.class, workspace.getId());
        assertThat(saved.getTitle()).isEqualTo("기존 제목");
        assertThat(saved.getContent()).isEqualTo("기존 내용");
    }
}
