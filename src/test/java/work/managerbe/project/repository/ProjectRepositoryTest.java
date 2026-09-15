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

@DataJpaTest
@Import(JpaAuditingConfig.class)
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
        Project first = Project.create("WORK_first", "첫 번째 프로젝트", null);
        Project second = Project.create("TASK_second", "두 번째 프로젝트", null);
        Project other = Project.create("OTHER_third", "다른 사용자 프로젝트", null);
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
        Project project = Project.create("WORK_first", "업무 관리 서비스", null);
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
}
