package work.managerbe.project.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import work.managerbe.board.domain.Board;
import work.managerbe.global.exception.board.BoardErrorCode;
import work.managerbe.global.exception.board.BoardException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.global.base.BaseEntity;
import work.managerbe.user.domain.User;

@Entity
@Getter
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long nextCardNumber;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User creator;


    /**
     * 양방향 일대다 목록의 인덱스를 Hibernate가 sort_order에 기록한다.
     * 보드 추가 시 목록과 Board.project를 함께 설정해 순서 갱신을 보장한다.
     */
    @OneToMany(mappedBy = "project")
    @OrderColumn(name = "sort_order")
    private List<Board> boards = new ArrayList<>();

    /**
     * 보드를 목록 끝에 추가하며 JPA가 목록 인덱스를 정렬 컬럼에 저장한다.
     */
    public Board addBoard(String name) {
        return Board.create(name, this);
    }

    /**
     * 이 프로젝트에서 생성한 보드만 목록에 등록하고 중복 등록으로 인한 순서 변경을 막는다.
     */
    public void registerBoard(Board board) {
        if (board == null || board.getProject() != this) {
            throw new IllegalArgumentException("이 프로젝트의 보드만 등록할 수 있습니다.");
        }
        if (!boards.contains(board)) {
            boards.add(board);
        }
    }

    /**
     * 보드 목록을 현재 순서대로 반환하고 외부의 직접 변경을 막는다.
     */
    public List<Board> getBoards() {
        return Collections.unmodifiableList(boards);
    }

    /**
     * 보드를 지정한 위치로 이동하고 영향받은 보드의 순서와 감사 시각을 동기화한다.
     */
    public void moveBoard(Board board, int position) {
        if (position < 0 || position >= boards.size()) {
            throw BoardException.of(
                    BoardErrorCode.BOARD_INVALID_SORT_ORDER);
        }
        int current = boards.indexOf(board);
        if (current < 0) {
            throw BoardException.of(
                    BoardErrorCode.BOARD_NOT_FOUND);
        }
        if (current != position) {
            boards.remove(current);
            boards.add(position, board);
            for (int index = Math.min(current, position); index <= Math.max(current, position); index++) {
                boards.get(index).synchronizeSortOrder(index);
            }
        }
    }

    @Builder
    private Project(User creator, String code, String name, long nextCardNumber, String description) {
        this.creator = creator;
        this.code = code;
        this.name = name;
        this.nextCardNumber = nextCardNumber;
        this.description = description;
    }

    public static Project create(User creator, String code, String name, String description) {
        return Project.builder()
                .creator(creator)
                .code(code)
                .name(name)
                .nextCardNumber(1L)
                .description(description)
                .build();
    }
}
