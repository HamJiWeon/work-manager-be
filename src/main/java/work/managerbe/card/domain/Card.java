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

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cards")
public class Card extends BaseEntity {

    private String username;

    private String title;

    private String content;

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
}