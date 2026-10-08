package work.managerbe.workspace.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import work.managerbe.global.exception.CommonException;
import work.managerbe.global.exception.ErrorCode;
import work.managerbe.global.exception.workspace.WorkspaceErrorCode;
import work.managerbe.global.exception.workspace.WorkspaceException;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;
import work.managerbe.workspace.domain.Workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 독립 트랜잭션에서 삭제를 커밋하고 새 조회로 물리 삭제와 경로 및 권한 제한을 검증한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-delete-integration-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkspaceDeleteIntegrationTest {

    private static final String PROJECT_CODE = "WORK";
    private static final String OTHER_PROJECT_CODE = "OTHER";
    private static final long MISSING_WORKSPACE_ID = Long.MAX_VALUE;

    private final WorkspaceService workspaceService;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    private User creator;
    private User otherUser;
    private Project project;
    private Project otherProject;
    private Workspace target;
    private Workspace sibling;
    private Workspace otherWorkspace;

    /** 각 테스트의 사용자와 두 프로젝트 및 워크스페이스를 커밋하여 준비한다. */
    @BeforeEach
    void 삭제_검증_데이터를_준비한다() {
        creator = User.create("생성자", "workspace-delete@example.com", null);
        otherUser = User.create("다른 사용자", "workspace-delete-other@example.com", null);
        project = Project.create(creator, PROJECT_CODE, "프로젝트", null);
        otherProject = Project.create(creator, OTHER_PROJECT_CODE, "다른 프로젝트", null);
        target = Workspace.create(project, "삭제 대상", "내용");
        sibling = Workspace.create(project, "보존 대상", "내용");
        otherWorkspace = Workspace.create(otherProject, "다른 프로젝트 문서", "내용");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.persist(creator);
            entityManager.persist(otherUser);
            entityManager.persist(project);
            entityManager.persist(otherProject);
            entityManager.persist(target);
            entityManager.persist(sibling);
            entityManager.persist(otherWorkspace);
        });
    }

    /** 커밋한 테스트 데이터를 자식부터 제거하여 테스트 간 영향을 방지한다. */
    @AfterEach
    void 삭제_검증_데이터를_정리한다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Workspace workspace : new Workspace[]{target, sibling, otherWorkspace}) {
                Workspace remaining = entityManager.find(Workspace.class, workspace.getId());
                if (remaining != null) {
                    entityManager.remove(remaining);
                }
            }
            entityManager.flush();
            entityManager.remove(entityManager.find(Project.class, project.getId()));
            entityManager.remove(entityManager.find(Project.class, otherProject.getId()));
            entityManager.flush();
            entityManager.remove(entityManager.find(User.class, creator.getId()));
            entityManager.remove(entityManager.find(User.class, otherUser.getId()));
        });
    }

    /** 서비스 트랜잭션이 커밋된 뒤 행 제거와 연관 데이터 보존을 확인한다. */
    @Test
    void 삭제_커밋_후_대상_행만_제거하고_프로젝트와_다른_워크스페이스는_보존한다() {
        // given
        Long targetId = target.getId();

        // when
        workspaceService.delete(creator.getId(), PROJECT_CODE, targetId, creator.getId());

        // then
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(entityManager.find(Workspace.class, targetId)).isNull();
            assertThat(entityManager.find(Project.class, project.getId())).isNotNull();
            assertThat(entityManager.find(Project.class, otherProject.getId())).isNotNull();
            assertThat(entityManager.find(User.class, creator.getId())).isNotNull();
            assertThat(entityManager.find(Workspace.class, sibling.getId())).isNotNull();
            assertThat(entityManager.find(Workspace.class, otherWorkspace.getId())).isNotNull();
        });
    }

    /** 동일 생성자의 다른 프로젝트 문서는 경로가 일치하지 않으면 보존한다. */
    @Test
    void 다른_프로젝트_경로로_삭제하면_찾을_수_없고_원본은_보존한다() {
        // given
        Long workspaceId = otherWorkspace.getId();

        // when / then
        assertThatThrownBy(() -> workspaceService.delete(creator.getId(), PROJECT_CODE, workspaceId, creator.getId()))
                .isInstanceOfSatisfying(WorkspaceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(entityManager.find(Workspace.class, workspaceId)).isNotNull());
    }

    /** 비소유자의 요청이 실패한 후에도 삭제 대상 행이 남아 있는지 확인한다. */
    @Test
    void 비소유자의_삭제는_거부하고_대상은_보존한다() {
        // given
        Long workspaceId = target.getId();

        // when / then
        assertThatThrownBy(() -> workspaceService.delete(creator.getId(), PROJECT_CODE, workspaceId, otherUser.getId()))
                .isInstanceOfSatisfying(CommonException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(entityManager.find(Workspace.class, workspaceId)).isNotNull());
    }

    /** 삭제 완료 후 새 요청은 대상 없음으로 실패하는지 확인한다. */
    @Test
    void 삭제한_워크스페이스를_재삭제하면_찾을_수_없다() {
        // given
        workspaceService.delete(creator.getId(), PROJECT_CODE, target.getId(), creator.getId());

        // when / then
        assertThatThrownBy(() -> workspaceService.delete(creator.getId(), PROJECT_CODE, target.getId(), creator.getId()))
                .isInstanceOfSatisfying(WorkspaceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
    }

    /** 존재하지 않는 ID의 요청이 대상 없음으로 실패하는지 확인한다. */
    @Test
    void 존재하지_않는_워크스페이스는_삭제할_수_없다() {
        // given
        Long workspaceId = MISSING_WORKSPACE_ID;

        // when / then
        assertThatThrownBy(() -> workspaceService.delete(creator.getId(), PROJECT_CODE, workspaceId, creator.getId()))
                .isInstanceOfSatisfying(WorkspaceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
    }
}
