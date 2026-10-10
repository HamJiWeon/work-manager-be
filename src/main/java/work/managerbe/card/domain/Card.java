package work.managerbe.card.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import work.managerbe.board.domain.Board;
import work.managerbe.global.base.BaseEntity;
import work.managerbe.member.domain.Member;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cards")
public class Card extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String username;

    private String title;

    private String content;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    @Column(nullable = false)
    private int sortOrder;

    private LocalDate startDate;

    private LocalDate endDate;

    private Card(
            User user, String username, String title, String content, CardStatus status, Member member,
            Project project, Board board, LocalDate startDate, LocalDate endDate
    ) {
        this.user = user;
        this.status = status;
        this.username = username;
        this.title = title;
        this.content = content;
        this.member = member;
        this.project = project;
        this.board = board;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 생성자와 담당자를 독립적으로 전달받아 요청한 상태와 연관 엔티티로 카드를 생성한다.
     */
    public static Card create(
            User user, String username, String title, String content, CardStatus status,
            Member member, Project project, Board board, LocalDate startDate, LocalDate endDate
    ) {
        return new Card(user, username, title, content, status,
                member, project, board, startDate, endDate);
    }

    /**
     * 같은 프로젝트 안에서 보드와 상태, 목록의 위치를 함께 갱신한다.
     */
    public void move(Board targetBoard, CardStatus targetStatus, int position) {

        if (targetBoard.getProject() != project || position < 0) {
            throw new IllegalArgumentException("카드 이동 대상이 유효하지 않습니다.");
        }

        board = targetBoard;
        status = targetStatus;
        sortOrder = position;
    }

    /**
     * 전달된 내용과 최종 일정을 반영한다. 날짜의 생략 여부는 서비스에서 처리한다.
     */
    public void update(String title, String content, LocalDate startDate, LocalDate endDate) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        this.startDate = startDate;
        this.endDate = endDate;
    }

}
