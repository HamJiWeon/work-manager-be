package work.managerbe.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.global.base.JoinEntity;
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
}