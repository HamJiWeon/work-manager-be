package work.managerbe.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.global.base.JoinEntity;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.global.exception.member.MemberErrorCode;
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

    private String role;

    /** 일반 역할 변경에서는 OWNER의 승격과 강등을 모두 거절한다. */
    public void changeRole(String role) {
        validateAssignableRole(role);
        if ("OWNER".equals(this.role)) {
            throw MemberException.of(
                    MemberErrorCode.MEMBER_OWNER_PROTECTED);
        }
        this.role = role;
    }

    /** 일반 멤버 추가와 수정은 ADMIN 또는 MEMBER 역할만 허용한다. */
    public static void validateAssignableRole(String role) {
        if ("OWNER".equals(role)) {
            throw MemberException.of(
                    MemberErrorCode.MEMBER_OWNER_PROTECTED);
        }
        if (!"MEMBER".equals(role) && !"ADMIN".equals(role)) {
            throw MemberException.of(
                    MemberErrorCode.MEMBER_INVALID_ROLE);
        }
    }

    @Builder
    private Member(User user, Project project, String role) {
        this.user = user;
        this.project = project;
        this.role = role;
    }

    /**
     * 전달받은 속성과 연관 엔티티로 새 프로젝트 참여자를 생성한다.
     */
    public static Member create(User user, Project project, String role) {
        return new Member(user, project, role);
    }
}