package work.managerbe.workspace.repository;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
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

    private static final int PAGE_SIZE = 10;

    /** 요청 크기 전후의 데이터 수에 따라 다음 페이지 여부와 반환 개수를 검증한다. */
    @ParameterizedTest
    @ValueSource(ints = {0, 9, 10, 11})
    void 목록_조회는_최대_열_개와_다음_페이지_여부를_반환한다(int count) {
        // given
        User creator = User.create("생성자", "slice@example.com", null);
        entityManager.persist(creator);
        Project project = Project.create(creator, "WORK", "프로젝트", null);
        entityManager.persist(project);
        for (int index = 0; index < count; index++) {
            entityManager.persist(Workspace.create(project, "제목 " + index, "내용"));
        }
        entityManager.flush();
        entityManager.clear();

        // when
        Slice<Workspace> result = workspaceRepository.findAllByProjectPath(
                creator.getId(), "WORK", PageRequest.of(0, PAGE_SIZE));

        // then
        assertThat(result.getContent()).hasSize(Math.min(count, PAGE_SIZE));
        assertThat(result.hasNext()).isEqualTo(count > PAGE_SIZE);
        assertThat(result.hasPrevious()).isFalse();
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(PAGE_SIZE);
    }

    /** 실제 DB 조회에서 프로젝트 범위, 생성일 동률 정렬 및 페이지 사이 중복을 검증한다. */
    @Test
    void 같은_생성일은_ID_내림차순으로_정렬하고_다른_프로젝트는_제외한다() {
        // given
        User creator = User.create("생성자", "slice@example.com", null);
        User otherCreator = User.create("다른 생성자", "other-slice@example.com", null);
        entityManager.persist(creator);
        entityManager.persist(otherCreator);
        Project project = Project.create(creator, "WORK", "프로젝트", null);
        Project otherCode = Project.create(creator, "OTHER", "다른 코드", null);
        Project otherOwner = Project.create(otherCreator, "WORK", "다른 생성자", null);
        entityManager.persist(project);
        entityManager.persist(otherCode);
        entityManager.persist(otherOwner);
        List<Workspace> workspaces = IntStream.range(0, 11)
                .mapToObj(index -> Workspace.create(project, "제목 " + index, "내용"))
                .toList();
        workspaces.forEach(entityManager::persist);
        entityManager.persist(Workspace.create(otherCode, "제외", "내용"));
        entityManager.persist(Workspace.create(otherOwner, "제외", "내용"));
        entityManager.flush();
        entityManager.createQuery("update Workspace w set w.createdAt = :createdAt")
                .setParameter("createdAt", java.time.LocalDateTime.of(2026, 10, 7, 9, 0))
                .executeUpdate();
        entityManager.clear();

        // when
        Slice<Workspace> first = workspaceRepository.findAllByProjectPath(
                creator.getId(), "WORK", PageRequest.of(0, PAGE_SIZE));
        Slice<Workspace> second = workspaceRepository.findAllByProjectPath(
                creator.getId(), "WORK", PageRequest.of(1, PAGE_SIZE));
        Slice<Workspace> beyond = workspaceRepository.findAllByProjectPath(
                creator.getId(), "WORK", PageRequest.of(2, PAGE_SIZE));

        // then
        assertThat(first.getContent()).extracting(Workspace::getId).containsExactly(
                workspaces.get(10).getId(), workspaces.get(9).getId(), workspaces.get(8).getId(),
                workspaces.get(7).getId(), workspaces.get(6).getId(), workspaces.get(5).getId(),
                workspaces.get(4).getId(), workspaces.get(3).getId(), workspaces.get(2).getId(),
                workspaces.get(1).getId());
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).extracting(Workspace::getId)
                .containsExactly(workspaces.getFirst().getId());
        assertThat(second.hasNext()).isFalse();
        assertThat(second.hasPrevious()).isTrue();
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.hasNext()).isFalse();
    }


    /** ID 순서와 생성일 순서가 다를 때 생성일이 정렬의 우선 기준인지 검증한다. */
    @Test
    void 생성일이_최신인_워크스페이스를_ID보다_우선하여_정렬한다() {
        // given
        User creator = User.create("생성자", "order@example.com", null);
        entityManager.persist(creator);
        Project project = Project.create(creator, "WORK", "프로젝트", null);
        entityManager.persist(project);
        Workspace newer = Workspace.create(project, "최신", "내용");
        Workspace older = Workspace.create(project, "이전", "내용");
        entityManager.persist(newer);
        entityManager.persist(older);
        entityManager.flush();
        entityManager.createQuery("update Workspace w set w.createdAt = :createdAt where w.id = :id")
                .setParameter("createdAt", java.time.LocalDateTime.of(2026, 10, 6, 9, 0))
                .setParameter("id", older.getId())
                .executeUpdate();
        entityManager.createQuery("update Workspace w set w.createdAt = :createdAt where w.id = :id")
                .setParameter("createdAt", java.time.LocalDateTime.of(2026, 10, 7, 9, 0))
                .setParameter("id", newer.getId())
                .executeUpdate();
        entityManager.clear();

        // when
        Slice<Workspace> result = workspaceRepository.findAllByProjectPath(
                creator.getId(), "WORK", PageRequest.of(0, PAGE_SIZE));

        // then
        assertThat(result.getContent()).extracting(Workspace::getId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(result.hasNext()).isFalse();
    }

}
