package work.managerbe.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.global.base.JoinEntity;
import work.managerbe.global.exception.member.MemberErrorCode;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "members")
public class Member extends JoinEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    @Builder
    private Member(User user, Project project, MemberRole role) {
        this.user = user;
        this.project = project;
        this.role = role;
    }

    /**
     * 전달받은 속성과 연관 엔티티로 새 프로젝트 참여자를 생성한다.
     */
    public static Member create(User user, Project project, MemberRole role) {
        return new Member(user, project, role);
    }

    /** 활성 멤버의 일반 역할만 변경하며 소유권 이전이나 탈퇴 이력 변경은 허용하지 않는다. */
    public void changeRole(MemberRole role) {
        if (getLeftAt() != null) {
            throw MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        if (this.role == MemberRole.OWNER) {
            throw MemberException.of(MemberErrorCode.MEMBER_OWNER_PROTECTED);
        }
        if (role != MemberRole.ADMIN && role != MemberRole.MEMBER) {
            throw MemberException.of(MemberErrorCode.MEMBER_INVALID_ROLE);
        }
        this.role = role;
    }
}
