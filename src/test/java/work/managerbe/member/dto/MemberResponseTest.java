package work.managerbe.member.dto;

import work.managerbe.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import work.managerbe.member.domain.Member;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 연관 ID와 가입·탈퇴·감사 시각의 응답 변환 및 null 처리를 검증한다.
 */
class MemberResponseTest {

    private static final Long MEMBER_ID = 10L;
    private static final Long PROJECT_ID = 20L;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final MemberRole ROLE = MemberRole.MEMBER;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 10, 0);
    private static final LocalDateTime JOINED_AT = CREATED_AT.plusMinutes(1);
    private static final LocalDateTime LEFT_AT = JOINED_AT.plusDays(1);
    private static final LocalDateTime UPDATED_AT = LEFT_AT.plusMinutes(1);

    @Test
    void 멤버의_모든_속성과_연관_ID를_응답으로_변환한다() {
        // given
        Member member = mock(Member.class);
        User user = mock(User.class);
        Project project = mock(Project.class);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(member.getUser()).thenReturn(user);
        when(member.getProject()).thenReturn(project);
        when(user.getId()).thenReturn(USER_ID);
        when(project.getId()).thenReturn(PROJECT_ID);
        when(member.getRole()).thenReturn(ROLE);
        when(member.getJoinedAt()).thenReturn(JOINED_AT);
        when(member.getLeftAt()).thenReturn(LEFT_AT);
        when(member.getCreatedAt()).thenReturn(CREATED_AT);
        when(member.getUpdatedAt()).thenReturn(UPDATED_AT);

        // when
        MemberResponse response = MemberResponse.from(member);

        // then
        assertThat(response.id()).isEqualTo(MEMBER_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.role()).isEqualTo(ROLE);
        assertThat(response.joinedAt()).isEqualTo(JOINED_AT);
        assertThat(response.leftAt()).isEqualTo(LEFT_AT);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void 연관_엔티티와_시각이_없어도_응답으로_변환한다() {
        // given
        Member member = Member.create(null, null, ROLE);

        // when
        MemberResponse response = MemberResponse.from(member);

        // then
        assertThat(response.id()).isNull();
        assertThat(response.userId()).isNull();
        assertThat(response.projectId()).isNull();
        assertThat(response.role()).isEqualTo(ROLE);
        assertThat(response.joinedAt()).isNull();
        assertThat(response.leftAt()).isNull();
        assertThat(response.createdAt()).isNull();
        assertThat(response.updatedAt()).isNull();
    }
}
