package work.managerbe.project.service;

import work.managerbe.member.domain.MemberRole;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.project.domain.Project;
import work.managerbe.project.dto.request.ProjectUpdateRequest;
import work.managerbe.board.domain.Board;
import work.managerbe.member.domain.Member;
import work.managerbe.workspace.domain.Workspace;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 실제 JPA 트랜잭션에서 프로젝트 수정 내용과 감사 시각이 DB 및 응답에 반영되는지 검증한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:project-service-integration-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Transactional
class ProjectServiceIntegrationTest {

    private static final LocalDateTime PREVIOUS_UPDATED_AT =
            LocalDateTime.of(2025, 1, 1, 0, 0);

    private final ProjectService projectService;
    private final EntityManager entityManager;

    @Autowired
    ProjectServiceIntegrationTest(ProjectService projectService, EntityManager entityManager) {
        this.projectService = projectService;
        this.entityManager = entityManager;
    }

    /**
     * 수정 서비스를 호출하면 새 이름과 설명 및 감사 수정 시각을 실제 DB에 저장하고 같은 값을 응답한다.
     */
    @Test
    void 프로젝트_수정은_이름과_수정시각을_DB와_응답에_반영한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        entityManager.persist(creator);
        Project project = Project.create(creator, "WORK", "기존 이름", "프로젝트 설명");
        entityManager.persist(project);
        entityManager.flush();

        Long projectId = project.getId();
        entityManager.createNativeQuery("""
                        update projects
                        set updated_at = :updatedAt
                        where id = :projectId
                        """)
                .setParameter("updatedAt", PREVIOUS_UPDATED_AT)
                .setParameter("projectId", projectId)
                .executeUpdate();
        entityManager.clear();

        // when
        var response = projectService.update(
                creator.getId(),
                "WORK",
                creator.getId(),
                new ProjectUpdateRequest("변경된 이름", "변경된 설명")
        );
        entityManager.clear();

        // then
        Project updatedProject = entityManager.find(Project.class, projectId);
        assertThat(response.name()).isEqualTo("변경된 이름");
        assertThat(response.description()).isEqualTo("변경된 설명");
        assertThat(updatedProject.getName()).isEqualTo("변경된 이름");
        assertThat(updatedProject.getDescription()).isEqualTo("변경된 설명");
        assertThat(response.updatedAt()).isAfter(PREVIOUS_UPDATED_AT);
        assertThat(updatedProject.getUpdatedAt())
                .isCloseTo(response.updatedAt(), within(1, ChronoUnit.MICROS));
    }

    /**
     * 프로젝트 삭제 시 DB cascade가 카드와 모든 프로젝트 소속 데이터를 제거하는지 검증한다.
     */
    @Test
    void 프로젝트를_삭제하면_관련_카드와_보드도_함께_삭제된다() {
        // given
        User creator = User.create("생성자", "delete-project@example.com", null);
        entityManager.persist(creator);
        Project project = Project.create(creator, "DELETE", "삭제할 프로젝트", null);
        entityManager.persist(project);
        Member member = Member.create(creator, project, MemberRole.OWNER);
        Board board = project.addBoard("진행 중");
        Workspace workspace = Workspace.create(project, "워크스페이스", "내용");
        entityManager.persist(member);
        entityManager.persist(board);
        entityManager.persist(workspace);
        entityManager.flush();

        entityManager.createNativeQuery("""
                        INSERT INTO cards (user_id, member_id, project_id, board_id,
                            title, content, created_at, updated_at)
                        VALUES (:userId, :memberId, :projectId, :boardId,
                            '카드', '내용', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """)
                .setParameter("userId", creator.getId())
                .setParameter("memberId", member.getId())
                .setParameter("projectId", project.getId())
                .setParameter("boardId", board.getId())
                .executeUpdate();

        Long projectId = project.getId();
        entityManager.clear();

        // when
        projectService.delete(creator.getId(), "DELETE", creator.getId());
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(countByProject("cards", projectId)).isZero();
        assertThat(countByProject("boards", projectId)).isZero();
        assertThat(countByProject("workspaces", projectId)).isZero();
        assertThat(countByProject("members", projectId)).isZero();
        assertThat(entityManager.find(Project.class, projectId)).isNull();
        assertThat(entityManager.find(User.class, creator.getId())).isNotNull();
    }

    private long countByProject(String tableName, Long projectId) {
        return ((Number) entityManager.createNativeQuery(
                        "select count(*) from " + tableName + " where project_id = :projectId")
                .setParameter("projectId", projectId)
                .getSingleResult()).longValue();
    }
}
