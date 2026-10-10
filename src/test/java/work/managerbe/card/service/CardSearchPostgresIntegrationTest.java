package work.managerbe.card.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import work.managerbe.board.domain.Board;
import work.managerbe.card.domain.Card;
import work.managerbe.card.domain.CardStatus;
import work.managerbe.card.dto.request.CardFilterRequest;
import work.managerbe.card.dto.response.CardResponse;
import work.managerbe.member.domain.Member;
import work.managerbe.member.domain.MemberRole;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL 16의 실제 QueryDSL 쿼리로 BIGINT 문자열 변환과 대소문자 검색 및 LIKE 이스케이프를 검증한다.
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
@Testcontainers
@Transactional
class CardSearchPostgresIntegrationTest {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String PROJECT_CODE = "WORK";
    private static final int FIRST_PAGE = 0;
    private static final int SINGLE_ITEM_PAGE_SIZE = 1;
    private static final int PAGE_SIZE = 20;

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    private final EntityManager entityManager;
    private final CardService service;

    @Autowired
    CardSearchPostgresIntegrationTest(EntityManager entityManager, CardService service) {
        this.entityManager = entityManager;
        this.service = service;
    }

    /**
     * 실제 BIGINT ID를 포함한 소문자 코드로 검색하고 반환된 카드 ID와 표시 코드를 확인한다.
     */
    @Test
    void 카드_ID를_문자열로_변환해_대소문자를_무시하고_검색한다() {
        // given
        Card selected = persistCard(PROJECT_CODE);
        CardFilterRequest filter = CardFilterRequest.of(null, null, null, " work-" + selected.getId() + " ");

        // when
        var response = service.getAll(selected.getUser().getId(), PROJECT_CODE, selected.getBoard().getId(),
                selected.getUser().getId(), FIRST_PAGE, PAGE_SIZE, filter);

        // then
        assertThat(response.items()).extracting(CardResponse::id).containsExactly(selected.getId());
        assertThat(response.items().getFirst().code()).isEqualTo(PROJECT_CODE + "-" + selected.getId());
        assertThat(response.hasNext()).isFalse();
    }

    /**
     * 혼합 대소문자 부분 검색 결과에 페이지 크기를 적용하고 다음 페이지 존재 여부를 확인한다.
     */
    @Test
    void 코드_부분_검색은_대소문자를_무시하고_페이징한다() {
        // given
        Card first = persistCard(PROJECT_CODE);
        Card second = Card.create(first.getUser(), "담당자", "두 번째 카드", "내용", CardStatus.NOT_STARTED,
                first.getMember(), first.getProject(), first.getBoard(), null, null);
        entityManager.persist(second);
        entityManager.flush();
        entityManager.clear();
        CardFilterRequest filter = CardFilterRequest.of(null, null, null, "oRk-");

        // when
        var response = service.getAll(first.getUser().getId(), PROJECT_CODE, first.getBoard().getId(),
                first.getUser().getId(), FIRST_PAGE, SINGLE_ITEM_PAGE_SIZE, filter);

        // then
        assertThat(response.items()).extracting(CardResponse::id).containsExactly(first.getId());
        assertThat(response.hasNext()).isTrue();
    }

    /**
     * 코드에 실제로 포함된 퍼센트와 밑줄을 일반 문자로 검색할 수 있는지 확인한다.
     */
    @ParameterizedTest
    @CsvSource({"WO%RK, %", "WO_RK, _"})
    void 코드에_포함된_와일드카드_문자를_리터럴로_검색한다(String projectCode, String search) {
        // given
        Card selected = persistCard(projectCode);
        CardFilterRequest filter = CardFilterRequest.of(null, null, null, search);

        // when
        var response = service.getAll(selected.getUser().getId(), projectCode, selected.getBoard().getId(),
                selected.getUser().getId(), FIRST_PAGE, PAGE_SIZE, filter);

        // then
        assertThat(response.items()).extracting(CardResponse::id).containsExactly(selected.getId());
        assertThat(response.hasNext()).isFalse();
    }

    /**
     * 퍼센트와 밑줄이 없는 코드는 해당 문자 검색에 매칭되지 않아 LIKE 와일드카드 확장을 방지하는지 확인한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "wOr%K-", "wOr_-"})
    void 와일드카드_문자가_없는_코드는_리터럴_검색에서_제외한다(String search) {
        // given
        Card selected = persistCard(PROJECT_CODE);
        CardFilterRequest filter = CardFilterRequest.of(null, null, null, search);

        // when
        var response = service.getAll(selected.getUser().getId(), PROJECT_CODE, selected.getBoard().getId(),
                selected.getUser().getId(), FIRST_PAGE, PAGE_SIZE, filter);

        // then
        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    /**
     * 활성 멤버와 보드 및 카드를 실제 DB에 저장하고 영속성 컨텍스트를 비워 DB 조회를 검증한다.
     */
    private Card persistCard(String projectCode) {
        User user = User.create("생성자", "card-search-postgres@test.com", null);
        entityManager.persist(user);
        Project project = Project.create(user, projectCode, "검색 프로젝트", null);
        entityManager.persist(project);
        Member member = Member.create(user, project, MemberRole.OWNER);
        entityManager.persist(member);
        Board board = Board.create("검색 보드", project);
        entityManager.persist(board);
        Card card = Card.create(user, "담당자", "검색 카드", "내용", CardStatus.NOT_STARTED,
                member, project, board, null, null);
        entityManager.persist(card);
        entityManager.flush();
        entityManager.clear();
        return card;
    }
}
