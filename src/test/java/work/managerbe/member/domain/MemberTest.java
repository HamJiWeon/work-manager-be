package work.managerbe.member.domain;

import work.managerbe.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import work.managerbe.global.exception.member.MemberErrorCode;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정적 팩터리의 연관 엔티티 및 역할 설정과 영속화 전 가입 상태를 검증한다.
 */
class MemberTest {

    private static final MemberRole ROLE = MemberRole.MEMBER;

    /** 엔티티에서도 소유권 이전을 제외한 역할만 변경한다. */
    @ParameterizedTest
    @EnumSource(value = MemberRole.class, names = {"ADMIN", "MEMBER"})
    void 일반_역할로_변경할_수_있다(MemberRole role) {
        // given
        Member member = Member.create(null, null, MemberRole.MEMBER);
        // when
        member.changeRole(role);
        // then
        assertThat(member.getRole()).isEqualTo(role);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = MemberRole.class, names = "OWNER")
    void OWNER나_null_역할로_변경할_수_없다(MemberRole role) {
        // given
        Member member = Member.create(null, null, MemberRole.MEMBER);
        // when / then
        assertThatThrownBy(() -> member.changeRole(role)).isInstanceOfSatisfying(MemberException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_INVALID_ROLE));
        assertThat(member.getRole()).isEqualTo(MemberRole.MEMBER);
    }

    @Test
    void OWNER_역할은_일반_수정으로_변경할_수_없다() {
        // given
        Member member = Member.create(null, null, MemberRole.OWNER);
        // when / then
        assertThatThrownBy(() -> member.changeRole(MemberRole.ADMIN)).isInstanceOfSatisfying(MemberException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_OWNER_PROTECTED));
        assertThat(member.getRole()).isEqualTo(MemberRole.OWNER);
    }

    @Test
    void 사용자와_프로젝트와_역할로_멤버를_생성한다() {
        // given
        User user = User.create("참여자", "member@example.com", null);
        Project project = Project.create(User.create("생성자", "creator@example.com", null), "TEST", "테스트 프로젝트", null);

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
