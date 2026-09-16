package work.managerbe.board.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import java.util.Objects;
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

    /**
     * 프로젝트 목록이 관리하는 sort_order 컬럼을 같은 이름으로 읽기 전용 매핑한다.
     */
    @Column(name = "sort_order", insertable = false, updatable = false)
    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private Board(String name, Project project) {
        this.name = name;
        this.sortOrder = project.getBoards().size();
        this.project = project;
    }

    /**
     * 프로젝트 목록 끝의 순서로 보드를 생성하고 양쪽 연관관계를 함께 등록한다.
     */
    public static Board create(String name, Project project) {
        Objects.requireNonNull(project, "프로젝트는 필수입니다.");
        Board board = new Board(name, project);
        project.registerBoard(board);
        return board;
    }
}
