package work.managerbe.card.domain;

import org.junit.jupiter.api.Test;
import work.managerbe.board.domain.Board;
import work.managerbe.member.domain.Member;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /** 이동은 보드·상태·위치를 함께 변경하고 다른 속성은 보존한다. */
    @Test
    void 같은_프로젝트_보드로_상태와_위치를_변경한다() {
        // given
        Project project = Project.create(null, "TEST", "프로젝트", null);
        Board source = Board.create("원본", project);
        Board target = Board.create("대상", project);
        Card card = Card.create(null, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                null, project, source, START_DATE, END_DATE);
        // when
        card.move(target, CardStatus.DONE, 2);
        // then
        assertThat(card.getBoard()).isSameAs(target);
        assertThat(card.getStatus()).isEqualTo(CardStatus.DONE);
        assertThat(card.getSortOrder()).isEqualTo(2);
        assertThat(card.getTitle()).isEqualTo(TITLE);
        assertThat(card.getEndDate()).isEqualTo(END_DATE);
    }

    /** 유효하지 않은 이동을 거절하면서 기존 카드 상태를 보존한다. */
    @Test
    void 외부_프로젝트와_음수_위치로_이동하면_거절한다() {
        // given
        Project project = Project.create(null, "TEST", "프로젝트", null);
        Board source = Board.create("원본", project);
        Board other = Board.create("외부", Project.create(null, "OTHER", "외부", null));
        Card card = Card.create(null, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                null, project, source, START_DATE, END_DATE);
        // when / then
        assertThatThrownBy(() -> card.move(other, CardStatus.DONE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> card.move(source, CardStatus.DONE, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(card.getBoard()).isSameAs(source);
        assertThat(card.getStatus()).isEqualTo(CardStatus.IN_PROGRESS);
        assertThat(card.getSortOrder()).isZero();
    }

    /** 생략된 내용은 보존하고 빈 내용과 명시적으로 전달한 날짜 삭제를 반영한다. */
    @Test
    void 제목과_본문의_null은_유지하고_빈_본문과_날짜_삭제를_반영한다() {
        // given
        Card card = Card.create(null, USERNAME, TITLE, CONTENT, CardStatus.IN_PROGRESS,
                null, null, null, START_DATE, END_DATE);
        // when
        card.update(null, null, START_DATE, END_DATE);
        // then
        assertThat(card.getTitle()).isEqualTo(TITLE);
        assertThat(card.getContent()).isEqualTo(CONTENT);
        // when
        card.update("수정 제목", "", null, null);
        // then
        assertThat(card.getTitle()).isEqualTo("수정 제목");
        assertThat(card.getContent()).isEmpty();
        assertThat(card.getStartDate()).isNull();
        assertThat(card.getEndDate()).isNull();
    }

}
