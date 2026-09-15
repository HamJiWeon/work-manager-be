package work.managerbe.member.domain;

import org.junit.jupiter.api.Test;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정적 팩터리의 연관 엔티티 및 역할 설정과 영속화 전 가입 상태를 검증한다.
 */
class MemberTest {

    private static final String ROLE = "MEMBER";

    @Test
    void 사용자와_프로젝트와_역할로_멤버를_생성한다() {
        // given
        User user = User.create("참여자", "member@example.com", null);
        Project project = Project.create("TEST", "테스트 프로젝트", null);

        // when
        Member member = Member.create(user, project, ROLE);

        // then
        assertThat(member.getUser()).isSameAs(user);
        assertThat(member.getProject()).isSameAs(project);
        assertThat(member.getRole()).isEqualTo(ROLE);
        assertThat(member.getId()).isNull();
        assertThat(member.getJoinedAt()).isNull();
        assertThat(member.getLeftAt()).isNull();
        assertThat(member.getCreatedAt()).isNull();
        assertThat(member.getUpdatedAt()).isNull();
    }
}
