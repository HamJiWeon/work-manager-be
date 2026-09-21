package work.managerbe.project.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import work.managerbe.global.config.JpaAuditingConfig;
import work.managerbe.global.config.QuerydslConfig;
import work.managerbe.member.domain.Member;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 참여 조회와 별개로 생성자 및 코드 기준의 존재 조회를 실제 JPA 쿼리로 검증한다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class ProjectRepositoryTest {

    private static final String ROLE = "MEMBER";

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void 사용자_저장() {
        user = User.create("참여자", "member@example.com", null);
        entityManager.persist(user);
    }

    @Test
    @DisplayName("해당 사용자가 참여 중인 프로젝트만 모두 조회한다.")
    void 사용자별_프로젝트_조회() {
        // given
        User otherUser = User.create("다른 참여자", "other@example.com", null);
        entityManager.persist(otherUser);
        Project first = Project.create(user, "WORK_first", "첫 번째 프로젝트", null);
        Project second = Project.create(user, "TASK_second", "두 번째 프로젝트", null);
        Project other = Project.create(otherUser, "OTHER_third", "다른 사용자 프로젝트", null);
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.persist(other);
        entityManager.persist(Member.create(user, first, ROLE));
        entityManager.persist(Member.create(user, second, ROLE));
        entityManager.persist(Member.create(otherUser, first, ROLE));
        entityManager.persist(Member.create(otherUser, other, ROLE));
        entityManager.flush();
        entityManager.clear();

        // when
        List<Project> projects = projectRepository.findByUser_Id(user.getId());

        // then
        assertThat(projects).extracting(Project::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    @DisplayName("사용자가 탈퇴한 프로젝트는 조회하지 않는다.")
    void 탈퇴한_프로젝트_제외() {
        // given
        Project project = Project.create(user, "WORK_first", "업무 관리 서비스", null);
        entityManager.persist(project);
        Member member = Member.create(user, project, ROLE);
        entityManager.persist(member);
        member.leave(member.getJoinedAt());
        entityManager.flush();
        entityManager.clear();

        // when
        List<Project> projects = projectRepository.findByUser_Id(user.getId());

        // then
        assertThat(projects).isEmpty();
    }

    @Test
    @DisplayName("참여한 프로젝트가 없으면 빈 목록을 반환한다.")
    void 참여한_프로젝트가_없으면_빈_목록_반환() {
        // given
        entityManager.flush();
        entityManager.clear();

        // when
        List<Project> projects = projectRepository.findByUser_Id(user.getId());

        // then
        assertThat(projects).isEmpty();
    }

    /**
     * 활성 참여 프로젝트만 이름순으로 정렬하고 요청한 페이지 크기만큼 조회한다.
     */
    @Test
    void 활성_프로젝트를_이름순으로_페이지_조회한다() {
        // given
        for (int index = 10; index >= 0; index--) {
            Project project = Project.create(
                    user,
                    "PROJECT_" + index,
                    "프로젝트 " + String.format("%02d", index),
                    null
            );
            entityManager.persist(project);
            entityManager.persist(Member.create(user, project, ROLE));
        }
        entityManager.flush();
        entityManager.clear();

        // when
        Slice<Project> firstPage = projectRepository.findActiveProjects(
                user.getId(), PageRequest.of(0, 10));
        Slice<Project> secondPage = projectRepository.findActiveProjects(
                user.getId(), PageRequest.of(1, 10));

        // then
        assertThat(firstPage.getContent()).extracting(Project::getName)
                .containsExactly(
                        "프로젝트 00", "프로젝트 01", "프로젝트 02", "프로젝트 03", "프로젝트 04",
                        "프로젝트 05", "프로젝트 06", "프로젝트 07", "프로젝트 08", "프로젝트 09"
                );
        assertThat(firstPage.hasPrevious()).isFalse();
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.getContent()).extracting(Project::getName)
                .containsExactly("프로젝트 10");
        assertThat(secondPage.hasPrevious()).isTrue();
        assertThat(secondPage.hasNext()).isFalse();
    }

    /**
     * OWNER 멤버 등록 없이도 프로젝트 생성자 필드와 정확한 코드 조합으로 조회한다.
     */
    @Test
    void 생성자와_코드가_모두_일치할_때만_존재한다() {
        // given
        Project project = Project.create(user, "WORK", "업무", null);
        entityManager.persist(project);
        entityManager.flush();
        entityManager.clear();

        // when / then
        assertThat(projectRepository.existsByCreator_IdAndCode(user.getId(), "WORK")).isTrue();
        assertThat(projectRepository.existsByCreator_IdAndCode(user.getId(), "STUDY")).isFalse();
        assertThat(projectRepository.existsByCreator_IdAndCode(user.getId(), "WOR")).isFalse();
    }

    /**
     * 다른 생성자의 프로젝트에 참여해도 자신의 코드 중복으로 판단하지 않는다.
     */
    @Test
    void 참여한_프로젝트의_코드는_자신의_생성_코드로_조회되지_않는다() {
        // given
        User otherCreator = User.create("다른 생성자", "other@example.com", null);
        entityManager.persist(otherCreator);
        Project project = Project.create(otherCreator, "WORK", "업무", null);
        entityManager.persist(project);
        entityManager.persist(Member.create(user, project, ROLE));
        entityManager.flush();
        entityManager.clear();

        // when / then
        assertThat(projectRepository.existsByCreator_IdAndCode(user.getId(), "WORK")).isFalse();
        assertThat(projectRepository.existsByCreator_IdAndCode(otherCreator.getId(), "WORK")).isTrue();
    }

    /**
     * 동일한 코드를 가진 두 프로젝트의 활성 참여자가 생성자별로 각각 조회할 수 있다.
     */
    @Test
    void 활성_참여자는_같은_코드의_프로젝트를_생성자별로_조회한다() {
        // given
        User firstCreator = User.create("첫 생성자", "first@example.com", null);
        User secondCreator = User.create("둘째 생성자", "second@example.com", null);
        entityManager.persist(firstCreator);
        entityManager.persist(secondCreator);
        Project first = Project.create(firstCreator, "WORK", "첫 프로젝트", null);
        Project second = Project.create(secondCreator, "WORK", "둘째 프로젝트", null);
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.persist(Member.create(firstCreator, first, "OWNER"));
        entityManager.persist(Member.create(secondCreator, second, "OWNER"));
        entityManager.persist(Member.create(user, first, ROLE));
        entityManager.persist(Member.create(user, second, ROLE));
        entityManager.flush();
        entityManager.clear();

        // when
        var firstFound = projectRepository.findAccessibleProject(firstCreator.getId(), "WORK", user.getId());
        var secondFound = projectRepository.findAccessibleProject(secondCreator.getId(), "WORK", user.getId());

        // then
        assertThat(firstFound).map(Project::getId).contains(first.getId());
        assertThat(secondFound).map(Project::getId).contains(second.getId());
        assertThat(projectRepository.findAccessibleProject(firstCreator.getId(), "WORK", firstCreator.getId()))
                .map(Project::getId).contains(first.getId());
        assertThat(projectRepository.findAccessibleProject(firstCreator.getId(), "WOR", user.getId())).isEmpty();
        assertThat(projectRepository.findAccessibleProject(user.getId(), "WORK", user.getId())).isEmpty();
    }

    /**
     * 생성자와 코드가 맞아도 요청자가 탈퇴했으면 조회하지 않는다.
     */
    @Test
    void 탈퇴한_참여자는_프로젝트를_조회할_수_없다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        entityManager.persist(creator);
        Project project = Project.create(creator, "WORK", "업무", null);
        entityManager.persist(project);
        entityManager.persist(Member.create(creator, project, "OWNER"));
        Member member = Member.create(user, project, ROLE);
        entityManager.persist(member);
        member.leave(member.getJoinedAt());
        entityManager.flush();
        entityManager.clear();

        // when
        var found = projectRepository.findAccessibleProject(creator.getId(), "WORK", user.getId());

        // then
        assertThat(found).isEmpty();
    }

    /**
     * 다른 활성 멤버가 있어도 요청자 자신의 멤버십이 없으면 조회하지 않는다.
     */
    @Test
    void 비참여자는_프로젝트를_조회할_수_없다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        entityManager.persist(creator);
        Project project = Project.create(creator, "WORK", "업무", null);
        entityManager.persist(project);
        entityManager.persist(Member.create(creator, project, "OWNER"));
        entityManager.flush();
        entityManager.clear();

        // when
        var found = projectRepository.findAccessibleProject(creator.getId(), "WORK", user.getId());

        // then
        assertThat(found).isEmpty();
    }

}
