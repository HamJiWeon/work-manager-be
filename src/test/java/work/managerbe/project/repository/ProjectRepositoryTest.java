package work.managerbe.project.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import work.managerbe.global.config.JpaAuditingConfig;
import work.managerbe.member.domain.Member;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 참여 조회와 별개로 생성자 및 코드 기준의 존재 조회를 실제 JPA 쿼리로 검증한다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class ProjectRepositoryTest {

    private static final String ROLE = "MEMBER";

    private final ProjectRepository projectRepository;
    private final EntityManager entityManager;

    @Autowired
    ProjectRepositoryTest(ProjectRepository projectRepository, EntityManager entityManager) {
        this.projectRepository = projectRepository;
        this.entityManager = entityManager;
    }

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

}
