package work.managerbe.card.dto;

import org.junit.jupiter.api.Test;
import work.managerbe.board.domain.Board;
import work.managerbe.card.domain.Card;
import work.managerbe.card.dto.response.CardResponse;
import work.managerbe.member.domain.Member;
import work.managerbe.project.domain.Project;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 서로 다른 연관 ID와 감사 시각으로 응답 매핑을 검증하고 null 연관 관계와 일정도 확인한다.
 */
class CardResponseTest {

    private static final Long CARD_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final Long PROJECT_ID = 30L;
    private static final Long BOARD_ID = 40L;
    private static final String USERNAME = "담당자";
    private static final String TITLE = "카드 제목";
    private static final String CONTENT = "카드 내용";
    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 15);
    private static final LocalDate END_DATE = START_DATE.plusDays(3);
    private static final LocalDateTime CREATED_AT = START_DATE.atStartOfDay();
    private static final LocalDateTime UPDATED_AT = CREATED_AT.plusHours(1);

    @Test
    void 카드의_모든_속성과_연관_ID를_응답으로_변환한다() {
        // given
        Card card = mock(Card.class);
        Member member = mock(Member.class);
        Project project = mock(Project.class);
        Board board = mock(Board.class);
        when(card.getId()).thenReturn(CARD_ID);
        when(card.getUsername()).thenReturn(USERNAME);
        when(card.getTitle()).thenReturn(TITLE);
        when(card.getContent()).thenReturn(CONTENT);
        when(card.getStatus()).thenReturn(work.managerbe.card.domain.CardStatus.DONE);
        when(card.getMember()).thenReturn(member);
        when(card.getProject()).thenReturn(project);
        when(card.getBoard()).thenReturn(board);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(project.getId()).thenReturn(PROJECT_ID);
        when(board.getId()).thenReturn(BOARD_ID);
        when(card.getStartDate()).thenReturn(START_DATE);
        when(card.getEndDate()).thenReturn(END_DATE);
        when(card.getCreatedAt()).thenReturn(CREATED_AT);
        when(card.getUpdatedAt()).thenReturn(UPDATED_AT);

        // when
        CardResponse response = CardResponse.from(card);

        // then
        assertThat(response.id()).isEqualTo(CARD_ID);
        assertThat(response.username()).isEqualTo(USERNAME);
        assertThat(response.title()).isEqualTo(TITLE);
        assertThat(response.content()).isEqualTo(CONTENT);
        assertThat(response.status()).isEqualTo(work.managerbe.card.domain.CardStatus.DONE);
        assertThat(response.memberId()).isEqualTo(MEMBER_ID);
        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.boardId()).isEqualTo(BOARD_ID);
        assertThat(response.startDate()).isEqualTo(START_DATE);
        assertThat(response.endDate()).isEqualTo(END_DATE);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void 연관_엔티티와_일정이_없어도_응답으로_변환한다() {
        // given
        Card card = Card.create(null, USERNAME, TITLE, CONTENT,
                work.managerbe.card.domain.CardStatus.NOT_STARTED, null, null, null, null, null);

        // when
        CardResponse response = CardResponse.from(card);

        // then
        assertThat(response.id()).isNull();
        assertThat(response.username()).isEqualTo(USERNAME);
        assertThat(response.title()).isEqualTo(TITLE);
        assertThat(response.content()).isEqualTo(CONTENT);
        assertThat(response.memberId()).isNull();
        assertThat(response.projectId()).isNull();
        assertThat(response.boardId()).isNull();
        assertThat(response.startDate()).isNull();
        assertThat(response.endDate()).isNull();
        assertThat(response.createdAt()).isNull();
        assertThat(response.updatedAt()).isNull();
    }
}
