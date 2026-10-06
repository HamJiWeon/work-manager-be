package work.managerbe.card.domain;

import org.junit.jupiter.api.Test;
import work.managerbe.board.domain.Board;
import work.managerbe.member.domain.Member;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정적 팩터리가 카드 속성과 연관 엔티티 및 선택 일정을 보존하는지 검증한다.
 */
class CardTest {

    private static final String USERNAME = "담당자";
    private static final String TITLE = "카드 제목";
    private static final String CONTENT = "카드 내용";
    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 15);
    private static final LocalDate END_DATE = START_DATE.plusDays(3);

    @Test
    void 전달한_속성과_연관_엔티티로_카드를_생성한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        Project project = Project.create(creator, "TEST", "테스트 프로젝트", null);
        User user = User.create(USERNAME, "test@example.com", null);
        Member member = Member.create(user, project, "MEMBER");
        Board board = Board.create("진행 중", project);

        // when
        Card card = Card.create(creator, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                member, project, board, START_DATE, END_DATE);

        // then
        assertThat(card.getUser()).isSameAs(creator);
        assertThat(card.getMember().getUser()).isSameAs(user);
        assertThat(card.getStatus()).isEqualTo(CardStatus.IN_PROGRESS);
        assertThat(card.getUsername()).isEqualTo(USERNAME);
        assertThat(card.getTitle()).isEqualTo(TITLE);
        assertThat(card.getContent()).isEqualTo(CONTENT);
        assertThat(card.getMember()).isSameAs(member);
        assertThat(card.getProject()).isSameAs(project);
        assertThat(card.getBoard()).isSameAs(board);
        assertThat(card.getStartDate()).isEqualTo(START_DATE);
        assertThat(card.getEndDate()).isEqualTo(END_DATE);
        assertThat(card.getId()).isNull();
        assertThat(card.getCreatedAt()).isNull();
        assertThat(card.getUpdatedAt()).isNull();
    }

    @Test
    void 일정이_없는_카드를_생성한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        Project project = Project.create(creator, "TEST", "테스트 프로젝트", null);
        User user = User.create(USERNAME, "test@example.com", null);
        Member member = Member.create(user, project, "MEMBER");
        Board board = Board.create("진행 중", project);

        // when
        Card card = Card.create(creator, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                member, project, board, null, null);

        // then
        assertThat(card.getStartDate()).isNull();
        assertThat(card.getEndDate()).isNull();
        assertThat(card.getTitle()).isEqualTo(TITLE);
    }
}
