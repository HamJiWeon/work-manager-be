package work.managerbe.board.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.global.base.BaseEntity;
import work.managerbe.global.exception.board.BoardErrorCode;
import work.managerbe.global.exception.board.BoardException;
import work.managerbe.project.domain.Project;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "boards")
public class Board extends BaseEntity {

    private static final int MAX_BOARD_COUNT = Integer.MAX_VALUE;

    private String name;

    /**
     * 프로젝트 목록이 관리하는 sort_order 컬럼을 같은 이름으로 읽기 전용 매핑한다.
     */
    @Column(name = "sort_order", insertable = false, updatable = false)
    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /**
     * 목록 크기를 다음 순서로 사용하되 추가 후 크기가 int 범위를 넘으면 생성 전에 거절한다.
     */
    private Board(String name, Project project) {
        int nextSortOrder = project.getBoards().size();
        if (nextSortOrder >= MAX_BOARD_COUNT) {
            throw BoardException.of(BoardErrorCode.BOARD_SORT_ORDER_EXHAUSTED);
        }
        this.name = name;
        this.sortOrder = nextSortOrder;
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

    /**
     * 공백 이름을 거절하고 변경된 이름을 감사 시각과 함께 반영한다.
     */
    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw BoardException.of(BoardErrorCode.BOARD_INVALID_NAME);
        }
        if (!Objects.equals(this.name, name)) {
            this.name = name;
            markUpdated();
        }
    }

    /**
     * 프로젝트 목록의 실제 위치로 읽기 전용 순서 필드와 감사 시각을 동기화한다.
     */
    public void synchronizeSortOrder(int position) {
        if (position < 0 || position >= project.getBoards().size() || project.getBoards().get(position) != this) {
            throw BoardException.of(BoardErrorCode.BOARD_NOT_FOUND);
        }
        if (sortOrder != position) {
            sortOrder = position;
            markUpdated();
        }
    }
}
