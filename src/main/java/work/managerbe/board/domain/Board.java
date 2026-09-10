package work.managerbe.board.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.global.base.BaseEntity;
import work.managerbe.project.domain.Project;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "boards")
public class Board extends BaseEntity {

    private String name;

    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Builder
    private Board(String name, int sortOrder, Project project) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.project = project;
    }

    /**
     * 전달받은 속성과 연관 엔티티로 새 보드를 생성한다.
     */
    public static Board create(String name, int sortOrder, Project project) {
        return new Board(name, sortOrder, project);
    }
}
