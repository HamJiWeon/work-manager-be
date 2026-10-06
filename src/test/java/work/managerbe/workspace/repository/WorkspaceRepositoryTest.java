package work.managerbe.workspace.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import work.managerbe.global.config.JpaAuditingConfig;
import work.managerbe.global.config.QuerydslConfig;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;
import work.managerbe.workspace.domain.Workspace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 워크스페이스 단건 조회가 ID뿐 아니라 프로젝트 생성자와 코드의 범위까지 제한하는지 검증한다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class WorkspaceRepositoryTest {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 워크스페이스_ID와_프로젝트_경로가_모두_일치할_때만_조회한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        User otherCreator = User.create("다른 생성자", "other@example.com", null);
        entityManager.persist(creator);
        entityManager.persist(otherCreator);

        Project project = Project.create(creator, "WORK", "업무 프로젝트", null);
        entityManager.persist(project);
        Workspace workspace = Workspace.create(project, "개발 가이드", "# 개발 가이드");
        entityManager.persist(workspace);
        entityManager.flush();
        entityManager.clear();

        // when / then
        assertThat(workspaceRepository.findByProjectPath(workspace.getId(), creator.getId(), "WORK"))
                .map(Workspace::getId)
                .contains(workspace.getId());
        assertThat(workspaceRepository.findByProjectPath(workspace.getId(), otherCreator.getId(), "WORK"))
                .isEmpty();
        assertThat(workspaceRepository.findByProjectPath(workspace.getId(), creator.getId(), "OTHER"))
                .isEmpty();
    }
}
