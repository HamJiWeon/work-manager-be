package work.managerbe.card.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
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

    private LocalDate startDate;

    private LocalDate endDate;

    @Builder
    private Card(
            String username, String title, String content, Member member,
            Project project, Board board, LocalDate startDate, LocalDate endDate
    ) {
        this.user = member == null ? null : member.getUser();
        this.status = CardStatus.NOT_STARTED;
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
     * 전달받은 속성과 연관 엔티티로 새 카드를 생성한다.
     */
    public static Card create(
            String username, String title, String content, Member member,
            Project project, Board board, LocalDate startDate, LocalDate endDate
    ) {
        return new Card(
                username,
                title,
                content,
                member,
                project,
                board,
                startDate,
                endDate
        );
    }
    /**
     * 인증된 생성자와 요청한 상태를 설정하여 저장 가능한 카드를 생성한다.
     */
    public static Card create(
            User user, String username, String title, String content, CardStatus status,
            Member member, Project project, Board board, LocalDate startDate, LocalDate endDate
    ) {
        Card card = create(username, title, content, member, project, board, startDate, endDate);
        card.user = user;
        card.status = status;
        return card;
    }
}