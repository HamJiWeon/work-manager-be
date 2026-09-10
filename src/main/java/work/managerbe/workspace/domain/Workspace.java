package work.managerbe.workspace.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.global.base.BaseEntity;
import work.managerbe.project.domain.Project;

@Entity
@Getter
@Table(name = "workspaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    private String title;

    private String content;

    @Builder
    private Workspace(Project project, String title, String content) {
        this.project = project;
        this.title = title;
        this.content = content;
    }

    public static Workspace create(Project project, String title, String content) {
        return Workspace.builder()
                .project(project)
                .title(title)
                .content(content)
                .build();
    }
}
