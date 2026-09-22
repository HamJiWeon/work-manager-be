package work.managerbe.member.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.member.domain.Member;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2와 Flyway 스키마로 멤버의 영속화, 탈퇴, 삭제 및 필수값과 프로젝트별 참여 유일성을 검증한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-repository-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Transactional
class MemberRepositoryTest {

    private static final String ROLE = "MEMBER";

    private final MemberRepository memberRepository;
    private final EntityManager entityManager;
    private User user;
    private Project project;

    @Test
    void 활성_멤버만_사용자와_프로젝트로_조회한다() {
        // given
        Member member = memberRepository.saveAndFlush(Member.create(user, project, ROLE));
        User otherUser = User.create("다른 사용자", "active-check@example.com", null);
        entityManager.persist(otherUser);

        // when / then
        assertThat(memberRepository.existsByUserIdAndProjectId(user.getId(), project.getId())).isTrue();
        assertThat(memberRepository.existsByUserIdAndProjectId(otherUser.getId(), project.getId())).isFalse();

        // given
        member.leave(LocalDateTime.now().plusSeconds(1));
        memberRepository.flush();

        // when / then
        assertThat(memberRepository.existsByUserIdAndProjectId(user.getId(), project.getId())).isFalse();
    }

    @Autowired
    MemberRepositoryTest(MemberRepository memberRepository, EntityManager entityManager) {
        this.memberRepository = memberRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void 사용자와_프로젝트를_저장한다() {
        user = User.create("참여자", "member@example.com", null);
        project = Project.create(user, "TEST", "테스트 프로젝트", null);
        entityManager.persist(user);
        entityManager.persist(project);
        entityManager.flush();
    }

    @Test
    void 멤버를_저장하고_연관_엔티티와_가입_시각을_조회한다() {
        // given
        Member member = Member.create(user, project, ROLE);

        // when
        Member saved = memberRepository.saveAndFlush(member);
        entityManager.clear();
        Member found = memberRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found).isNotSameAs(saved);
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getUser().getName()).isEqualTo(user.getName());
        assertThat(found.getProject().getId()).isEqualTo(project.getId());
        assertThat(found.getProject().getName()).isEqualTo(project.getName());
        assertThat(found.getRole()).isEqualTo(ROLE);
        assertThat(found.getJoinedAt()).isNotNull();
        assertThat(found.getLeftAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 탈퇴_시각을_저장해도_가입_시각은_유지된다() {
        // given
        Member member = memberRepository.saveAndFlush(Member.create(user, project, ROLE));
        entityManager.clear();
        Member found = memberRepository.findById(member.getId()).orElseThrow();
        LocalDateTime joinedAt = found.getJoinedAt();
        LocalDateTime leftAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        // when
        found.leave(leftAt);
        memberRepository.flush();
        entityManager.clear();
        Member reloaded = memberRepository.findById(member.getId()).orElseThrow();

        // then
        assertThat(reloaded.getLeftAt()).isEqualTo(leftAt);
        assertThat(reloaded.getJoinedAt()).isEqualTo(joinedAt);
    }

    @Test
    void 멤버를_삭제해도_사용자와_프로젝트는_유지된다() {
        // given
        Member member = memberRepository.saveAndFlush(Member.create(user, project, ROLE));
        entityManager.clear();

        // when
        memberRepository.deleteById(member.getId());
        memberRepository.flush();
        entityManager.clear();

        // then
        assertThat(memberRepository.findById(member.getId())).isEmpty();
        assertThat(entityManager.find(User.class, user.getId())).isNotNull();
        assertThat(entityManager.find(Project.class, project.getId())).isNotNull();
    }

    @Test
    void 동일_사용자가_동일_프로젝트에_중복_참여할_수_없다() {
        // given
        memberRepository.saveAndFlush(Member.create(user, project, ROLE));
        Member duplicate = Member.create(user, project, ROLE);

        // when / then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 동일_사용자가_서로_다른_프로젝트에_참여할_수_있다() {
        // given
        Member first = memberRepository.saveAndFlush(Member.create(user, project, ROLE));
        Project otherProject = Project.create(user, "OTHER", "다른 프로젝트", null);
        entityManager.persist(otherProject);

        // when
        Member second = memberRepository.saveAndFlush(Member.create(user, otherProject, ROLE));
        entityManager.clear();

        // then
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(memberRepository.findById(first.getId())).isPresent();
        assertThat(memberRepository.findById(second.getId())).isPresent();
    }

    @Test
    void 서로_다른_사용자가_동일_프로젝트에_참여할_수_있다() {
        // given
        Member first = memberRepository.saveAndFlush(Member.create(user, project, ROLE));
        User otherUser = User.create("다른 참여자", "other@example.com", null);
        entityManager.persist(otherUser);

        // when
        Member second = memberRepository.saveAndFlush(Member.create(otherUser, project, ROLE));
        entityManager.clear();

        // then
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(memberRepository.findById(first.getId())).isPresent();
        assertThat(memberRepository.findById(second.getId())).isPresent();
    }

    @Test
    void 사용자가_없는_멤버는_저장할_수_없다() {
        // given
        Member member = Member.create(null, project, ROLE);

        // when / then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(member))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 프로젝트가_없는_멤버는_저장할_수_없다() {
        // given
        Member member = Member.create(user, null, ROLE);

        // when / then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(member))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 역할이_없는_멤버는_저장할_수_없다() {
        // given
        Member member = Member.create(user, project, null);

        // when / then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(member))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
