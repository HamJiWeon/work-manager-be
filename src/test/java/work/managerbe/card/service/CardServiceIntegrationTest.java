package work.managerbe.card.service;

import work.managerbe.member.domain.MemberRole;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;
import work.managerbe.card.dto.request.CardUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.board.repository.BoardRepository;
import work.managerbe.card.domain.CardStatus;
import work.managerbe.card.domain.Card;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.dto.request.CardFilterRequest;
import work.managerbe.card.dto.response.CardResponse;
import work.managerbe.card.repository.CardRepository;
import work.managerbe.global.exception.board.BoardException;
import work.managerbe.global.exception.board.BoardErrorCode;
import work.managerbe.global.exception.card.CardException;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.global.exception.card.CardErrorCode;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.member.domain.Member;
import work.managerbe.member.repository.MemberRepository;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;

/**
 * 실제 Flyway 스키마에서 카드 저장 및 프로젝트 소속, 권한, 일정 검증을 확인한다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:card-create-test;DB_CLOSE_DELAY=-1")
@Transactional
class CardServiceIntegrationTest {
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final CardService service;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final BoardRepository boards;
    private final MemberRepository members;
    private final CardRepository cards;
    private User creator;
    private User requester;
    private Project project;
    private Board board;
    private Member assignee;

    @Autowired
    CardServiceIntegrationTest(CardService service, UserRepository users, ProjectRepository projects,
                               BoardRepository boards, MemberRepository members, CardRepository cards, EntityManager entityManager, ObjectMapper mapper) {
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.service = service;
        this.users = users;
        this.projects = projects;
        this.boards = boards;
        this.members = members;
        this.cards = cards;
    }

    @BeforeEach
    void 데이터_준비() {
        creator = users.save(User.create("생성자", "creator@test.com", null));
        requester = users.save(User.create("요청자", "requester@test.com", null));
        project = projects.save(Project.create(creator, "WORK", "프로젝트", null));
        members.save(Member.create(requester, project, MemberRole.MEMBER));
        assignee = members.save(Member.create(creator, project, MemberRole.OWNER));
        board = boards.save(Board.create("보드", project));
    }

    /**
     * 날짜 하한과 상한을 각각 적용하고 선택된 날짜의 오름차순과 동률 ID 순서를 검증한다.
     */
    @Test
    void 날짜_필터는_경계를_포함하고_입력한_날짜_기준으로_정렬한다() {
        // given
        var laterStart = create(request(project.getId(), board.getId(), assignee.getId(),
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16)), requester.getId());
        var earlierStart = create(request(project.getId(), board.getId(), assignee.getId(),
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 20)), requester.getId());
        var beforeRange = create(request(project.getId(), board.getId(), assignee.getId(),
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12)), requester.getId());
        var sameDate = create(request(project.getId(), board.getId(), assignee.getId(),
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 20)), requester.getId());
        create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        // when
        var byStart = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 20,
                CardFilterRequest.of(null, LocalDate.of(2026, 9, 11), null, null));
        var byEnd = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 20,
                CardFilterRequest.of(null, null, LocalDate.of(2026, 9, 20), null));
        var both = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 20,
                CardFilterRequest.of(null, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 16), null));
        // then
        assertThat(byStart.items()).extracting(CardResponse::id).containsExactly(earlierStart.id(), sameDate.id(), laterStart.id());
        assertThat(byEnd.items()).extracting(CardResponse::id).containsExactly(beforeRange.id(), laterStart.id(), earlierStart.id(), sameDate.id());
        assertThat(both.items()).extracting(CardResponse::id).containsExactly(laterStart.id());
    }

    /**
     * ID 기반 카드 코드를 부분 검색하고 필터들의 AND 조합과 검색 후 페이징을 검증한다.
     */
    @Test
    void 카드_코드_부분_검색은_대소문자를_무시하고_다른_필터와_조합한다() {
        // given
        var selected = create(request(project.getId(), board.getId(), assignee.getId(),
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 15)), requester.getId());
        create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        // when
        var combined = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 1,
                CardFilterRequest.of(assignee.getId(), LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 15),
                        " work-" + selected.id() + " "));
        var partial = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 1,
                CardFilterRequest.of(null, null, null, "wOrK-"));
        var blank = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 20,
                CardFilterRequest.of(null, null, null, " "));
        var wildcard = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 20,
                CardFilterRequest.of(null, null, null, "%"));
        // then
        assertThat(combined.items()).extracting(CardResponse::id).containsExactly(selected.id());
        assertThat(combined.items().getFirst().code()).isEqualTo("WORK-" + selected.id());
        assertThat(combined.hasNext()).isFalse();
        assertThat(partial.items()).hasSize(1);
        assertThat(partial.hasNext()).isTrue();
        assertThat(blank.items()).hasSize(2);
        assertThat(wildcard.items()).isEmpty();
    }

    /**
     * 역전된 기간과 잘못된 담당자 및 누락된 필터 요청은 공개 조회 오류로 거절한다.
     */
    @Test
    void 잘못된_필터_조건을_거절한다() {
        // given
        var reversed = CardFilterRequest.of(null, LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 11), null);
        var invalidMember = CardFilterRequest.of(0L, null, null, null);
        // when / then
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 20, reversed))
                .isInstanceOfSatisfying(CardException.class, error -> assertThat(error.getErrorCode()).isEqualTo(CardErrorCode.CARD_INVALID_FILTER));
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 20, invalidMember))
                .isInstanceOf(CardException.class);
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 20, (CardFilterRequest) null))
                .isInstanceOf(CardException.class);
    }

    /**
     * 담당자 필터를 페이징 이전에 적용하고 결과가 정확히 한 페이지면 다음 목록이 없음을 검증한다.
     */
    @Test
    void 멤버_필터는_해당_담당자의_카드만_반환한다() {
        // given
        var selected = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var otherMember = members.save(Member.create(users.save(User.create("다른 담당자", "other-assignee@test.com", null)), project, MemberRole.MEMBER));
        create(request(project.getId(), board.getId(), otherMember.getId(), null, null), requester.getId());
        // when
        var response = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 1, CardFilterRequest.of(assignee.getId(), null, null, null));
        var empty = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 1, CardFilterRequest.of(Long.MAX_VALUE, null, null, null));
        // then
        assertThat(response.items()).extracting(CardResponse::id).containsExactly(selected.id());
        assertThat(response.hasNext()).isFalse();
        assertThat(empty.items()).isEmpty();
        assertThat(empty.hasNext()).isFalse();
    }

    /**
     * 다른 보드를 제외하고 저장된 순서, 페이지 경계와 다음 데이터 존재 여부를 검증한다.
     */
    @Test
    void 카드_목록은_보드별로_순서와_다음_데이터_존재_여부를_반환한다() {
        // given
        var first = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var second = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var third = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var otherBoard = boards.save(Board.create("다른 보드", project));
        service.create(creator.getId(), project.getCode(), otherBoard.getId(), requester.getId(),
                request(project.getId(), otherBoard.getId(), assignee.getId(), null, null));
        update(third.id(), "{\"sortOrder\":0}");
        // when
        var firstPage = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 2);
        var lastPage = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 1, 2);
        var beyond = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 2, 2);
        // then
        assertThat(firstPage.items()).extracting(CardResponse::id).containsExactly(third.id(), first.id());
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(lastPage.hasNext()).isFalse();
        assertThat(lastPage.items()).extracting(CardResponse::id).containsExactly(second.id());
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.hasNext()).isFalse();
    }

    /**
     * 빈 보드는 빈 목록과 다음 데이터가 없음을 반환한다.
     */
    @Test
    void 빈_보드의_카드_목록은_다음_데이터가_없다() {
        // given / when
        var response = service.getAll(creator.getId(), project.getCode(), board.getId(), requester.getId(), 0, 20);
        // then
        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    /**
     * 외부인 및 다른 프로젝트나 존재하지 않는 보드의 조회를 거절한다.
     */
    @Test
    void 카드_목록은_활성_멤버와_보드_소속을_검증한다() {
        // given
        var outsider = users.save(User.create("외부인", "list-outsider@test.com", null));
        var otherProject = projects.save(Project.create(creator, "OTHER", "다른 프로젝트", null));
        var otherBoard = boards.save(Board.create("다른 보드", otherProject));
        // when / then
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), board.getId(), outsider.getId(), 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), otherBoard.getId(), requester.getId(), 0, 20))
                .isInstanceOf(BoardException.class);
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), Long.MAX_VALUE, requester.getId(), 0, 20))
                .isInstanceOf(BoardException.class);
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), board.getId(), null, 0, 20))
                .isInstanceOf(UserException.class);
    }

    /**
     * 저장되지 않은 요청자 ID로 목록을 조회하면 사용자 없음 오류를 반환하는지 검증한다.
     */
    @Test
    void 카드_목록은_존재하지_않는_요청자를_거절한다() {
        // given
        UUID missingRequesterId = UUID.randomUUID();
        CardFilterRequest filter = CardFilterRequest.of(null, null, null, null);

        // when / then
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), board.getId(),
                missingRequesterId, 0, 20, filter))
                .isInstanceOfSatisfying(UserException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * 존재하는 요청자가 미등록 프로젝트 코드를 조회하면 프로젝트 없음 오류를 반환하는지 검증한다.
     */
    @Test
    void 카드_목록은_존재하지_않는_프로젝트를_거절한다() {
        // given
        String missingProjectCode = "MISSING";
        CardFilterRequest filter = CardFilterRequest.of(null, null, null, null);

        // when / then
        assertThatThrownBy(() -> service.getAll(creator.getId(), missingProjectCode, board.getId(),
                requester.getId(), 0, 20, filter))
                .isInstanceOfSatisfying(ProjectException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    /**
     * 활성 멤버가 보드 ID 없이 목록을 조회하면 보드 없음 오류를 반환하는지 검증한다.
     */
    @Test
    void 카드_목록은_보드_ID_누락을_거절한다() {
        // given
        CardFilterRequest filter = CardFilterRequest.of(null, null, null, null);

        // when / then
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), null,
                requester.getId(), 0, 20, filter))
                .isInstanceOfSatisfying(BoardException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(BoardErrorCode.BOARD_NOT_FOUND));
    }

    @Test
    void 요청한_상태와_생성자_담당자를_구분하여_카드를_저장한다() {
        // given
        var request = request(project.getId(), board.getId(), assignee.getId(), null, null);
        // when
        var response = create(request, requester.getId());
        // then
        var saved = cards.findById(response.id()).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(requester.getId());
        assertThat(saved.getMember().getId()).isEqualTo(assignee.getId());
        assertThat(response.status()).isEqualTo(CardStatus.IN_PROGRESS);
        assertThat(response.username()).isEqualTo("홍길동");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.boardId()).isEqualTo(board.getId());
    }

    @Test
    void 비참여자는_생성할_수_없다() {
        // given
        User outsider = users.save(User.create("외부인", "outsider@test.com", null));
        var request = request(project.getId(), board.getId(), assignee.getId(), null, null);
        // when / then
        assertThatThrownBy(() -> create(request, outsider.getId())).isInstanceOf(AccessDeniedException.class);
        assertThat(cards.count()).isZero();
    }

    @Test
    void 본문의_프로젝트나_보드가_경로와_다르면_거절한다() {
        // given
        var wrongProject = request(Long.MAX_VALUE, board.getId(), assignee.getId(), null, null);
        var wrongBoard = request(project.getId(), Long.MAX_VALUE, assignee.getId(), null, null);
        // when / then
        assertThatThrownBy(() -> create(wrongProject, requester.getId())).isInstanceOf(CardException.class);
        assertThatThrownBy(() -> create(wrongBoard, requester.getId())).isInstanceOf(CardException.class);
        assertThat(cards.count()).isZero();
    }

    @Test
    void 다른_프로젝트의_담당자와_보드는_거절한다() {
        // given
        Project other = projects.save(Project.create(creator, "OTHER", "다른 프로젝트", null));
        Member otherMember = members.save(Member.create(requester, other, MemberRole.MEMBER));
        Board otherBoard = boards.save(Board.create("다른 보드", other));
        var wrongMember = request(project.getId(), board.getId(), otherMember.getId(), null, null);
        var wrongBoard = request(project.getId(), otherBoard.getId(), assignee.getId(), null, null);
        // when / then
        assertThatThrownBy(() -> create(wrongMember, requester.getId())).isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> service.create(creator.getId(), project.getCode(), otherBoard.getId(),
                requester.getId(), wrongBoard)).isInstanceOf(BoardException.class);
        assertThat(cards.count()).isZero();
    }

    @Test
    void 탈퇴한_담당자와_요청자는_거절한다() {
        // given
        assignee.leave(LocalDateTime.now().plusSeconds(1));
        var request = request(project.getId(), board.getId(), assignee.getId(), null, null);
        // when / then
        assertThatThrownBy(() -> create(request, requester.getId())).isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> create(request, creator.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 종료일이_시작일보다_이르면_거절한다() {
        // given
        var request = request(project.getId(), board.getId(), assignee.getId(),
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 11));
        // when / then
        assertThatThrownBy(() -> create(request, requester.getId())).isInstanceOf(CardException.class);
        assertThat(cards.count()).isZero();
    }

    /**
     * 공개 생성 메서드를 호출해 null 요청이 저장 전에 거절되는지 확인한다.
     */
    @Test
    void 생성_요청이_없으면_거절한다() {
        // given / when / then
        assertThatThrownBy(() -> create(null, requester.getId()))
                .isInstanceOfSatisfying(CardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CardErrorCode.CARD_INVALID_REQUEST));
        assertThat(cards.count()).isZero();
    }

    /**
     * 요청자 ID 누락을 사용자 오류로 반환하고 카드를 저장하지 않는지 확인한다.
     */
    @Test
    void 요청자_ID가_없으면_거절한다() {
        // given
        var request = request(project.getId(), board.getId(), assignee.getId(), null, null);
        // when / then
        assertThatThrownBy(() -> create(request, null))
                .isInstanceOfSatisfying(UserException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
        assertThat(cards.count()).isZero();
    }

    /**
     * 프로젝트 생성자 ID가 없을 때 프로젝트 오류로 거절하는지 확인한다.
     */
    @Test
    void 프로젝트_생성자_ID가_없으면_거절한다() {
        // given
        var request = request(project.getId(), board.getId(), assignee.getId(), null, null);
        // when / then
        assertThatThrownBy(() -> service.create(null, project.getCode(), board.getId(), requester.getId(), request))
                .isInstanceOfSatisfying(ProjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND));
        assertThat(cards.count()).isZero();
    }

    /**
     * 프로젝트 코드의 null, 빈 문자열 및 공백을 각각 공개 메서드로 검증한다.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void 프로젝트_코드가_없거나_공백이면_거절한다(String code) {
        // given
        var request = request(project.getId(), board.getId(), assignee.getId(), null, null);
        // when / then
        assertThatThrownBy(() -> service.create(creator.getId(), code, board.getId(), requester.getId(), request))
                .isInstanceOfSatisfying(ProjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND));
        assertThat(cards.count()).isZero();
    }

    /**
     * 저장된 카드 전체 정보와 날짜가 단건 조회에서도 유지되는지 확인한다.
     */
    @Test
    void 활성_멤버는_카드를_단건_조회한다() {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(),
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 15)), requester.getId());
        // when
        var response = service.get(creator.getId(), project.getCode(), board.getId(), saved.id(), requester.getId());
        // then
        assertThat(response).isEqualTo(saved);
    }

    /**
     * 없는 카드와 경로의 보드 또는 프로젝트가 다른 카드를 같은 오류로 거절한다.
     */
    @Test
    void 카드가_없거나_경로_소속이_다르면_거절한다() {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var other = projects.save(Project.create(creator, "OTHER", "다른 프로젝트", null));
        members.save(Member.create(requester, other, MemberRole.MEMBER));
        var otherBoard = boards.save(Board.create("다른 보드", other));
        // when / then
        assertThatThrownBy(() -> service.get(creator.getId(), project.getCode(), board.getId(),
                Long.MAX_VALUE, requester.getId()))
                .isInstanceOfSatisfying(CardException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CardErrorCode.CARD_NOT_FOUND));
        assertThatThrownBy(() -> service.get(creator.getId(), project.getCode(), otherBoard.getId(),
                saved.id(), requester.getId()))
                .isInstanceOfSatisfying(CardException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CardErrorCode.CARD_NOT_FOUND));
        assertThatThrownBy(() -> service.get(creator.getId(), other.getCode(), board.getId(),
                saved.id(), requester.getId()))
                .isInstanceOfSatisfying(CardException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CardErrorCode.CARD_NOT_FOUND));
    }

    /**
     * 비참여자와 탈퇴한 멤버의 조회를 거절한다.
     */
    @Test
    void 비참여자와_탈퇴자는_카드를_조회할_수_없다() {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var outsider = users.save(User.create("외부인", "outsider@test.com", null));
        assignee.leave(LocalDateTime.now().plusSeconds(1));
        // when / then
        assertThatThrownBy(() -> service.get(creator.getId(), project.getCode(), board.getId(),
                saved.id(), outsider.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.get(creator.getId(), project.getCode(), board.getId(),
                saved.id(), creator.getId())).isInstanceOf(AccessDeniedException.class);
    }

    /**
     * 미가입 요청자가 카드 조회 전에 사용자 없음 오류로 거절되는지 확인한다.
     */
    @Test
    void 미가입_요청자는_카드를_조회할_수_없다() {
        // given
        UUID unknownRequesterId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var saved = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        // when / then
        assertThatThrownBy(() -> service.get(creator.getId(), project.getCode(), board.getId(),
                saved.id(), unknownRequesterId))
                .isInstanceOfSatisfying(UserException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * 요청자 ID 누락 시 단건 조회에서도 사용자 없음 오류를 반환하는지 확인한다.
     */
    @Test
    void 조회_요청자_ID가_없으면_거절한다() {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        // when / then
        assertThatThrownBy(() -> service.get(creator.getId(), project.getCode(), board.getId(), saved.id(), null))
                .isInstanceOfSatisfying(UserException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * 카드와 보드 ID의 개별 및 동시 누락을 검증해 조건식의 각 분기를 확인한다.
     */
    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "true, true"})
    void 조회_카드나_보드_ID가_없으면_거절한다(boolean missingCardId, boolean missingBoardId) {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        Long cardId = missingCardId ? null : saved.id();
        Long boardId = missingBoardId ? null : board.getId();
        // when / then
        assertThatThrownBy(() -> service.get(creator.getId(), project.getCode(), boardId, cardId, requester.getId()))
                .isInstanceOfSatisfying(CardException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CardErrorCode.CARD_NOT_FOUND));
    }

    /**
     * 경로의 프로젝트가 존재하지 않으면 조회에서 프로젝트 없음 오류를 반환하는지 확인한다.
     */
    @Test
    void 조회_대상_프로젝트가_없으면_거절한다() {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        String unknownProjectCode = "MISSING";
        // when / then
        assertThatThrownBy(() -> service.get(creator.getId(), unknownProjectCode, board.getId(),
                saved.id(), requester.getId()))
                .isInstanceOfSatisfying(ProjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND));
    }


    @Test
    void 같은_상태_순서를_바꾸고_다시_조회해도_유지한다() {
        // given
        var first = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var second = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var third = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        // when
        var response = update(third.id(), "{\"sortOrder\":0}");
        entityManager.flush();
        entityManager.clear();
        // then
        assertThat(response.sortOrder()).isZero();
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getId).containsExactly(third.id(), first.id(), second.id());
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getSortOrder).containsExactly(0, 1, 2);
    }

    @Test
    void 상태_변경과_대상_위치를_함께_저장하고_원래_목록을_정리한다() {
        // given
        var first = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var second = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var third = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        update(second.id(), "{\"status\":\"DONE\"}");
        // when
        var response = update(first.id(), "{\"status\":\"DONE\",\"sortOrder\":0}");
        entityManager.clear();
        // then
        assertThat(response.status()).isEqualTo(CardStatus.DONE);
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getId).containsExactly(third.id());
        assertThat(cards.findById(third.id()).orElseThrow().getSortOrder()).isZero();
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.DONE))
                .extracting(Card::getId).containsExactly(first.id(), second.id());
    }

    @Test
    void 제목만_수정하면_상태_순서_날짜를_유지하고_명시한_null은_날짜를_삭제한다() {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(),
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 15)), requester.getId());
        // when
        var renamed = update(saved.id(), "{\"title\":\"새 제목\",\"content\":\"\"}");
        // then
        assertThat(renamed.title()).isEqualTo("새 제목");
        assertThat(renamed.content()).isEmpty();
        assertThat(renamed.endDate()).isEqualTo(saved.endDate());
        assertThat(renamed.status()).isEqualTo(saved.status());
        // when
        var cleared = update(saved.id(), "{\"endDate\":null,\"startDate\":null}");
        // then
        assertThat(cleared.startDate()).isNull();
        assertThat(cleared.endDate()).isNull();
    }

    @Test
    void 같은_프로젝트의_다른_보드로_이동한다() {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var target = boards.save(Board.create("대상 보드", project));
        // when
        var moved = update(saved.id(), "{\"boardId\":" + target.getId() + "}");
        entityManager.clear();
        // then
        assertThat(moved.boardId()).isEqualTo(target.getId());
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), saved.status())).isEmpty();
        assertThat(service.get(creator.getId(), project.getCode(), target.getId(), saved.id(), requester.getId()).sortOrder()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\" \"}", "{\"sortOrder\":-1}", "{\"boardId\":0}", "{\"sortOrder\":2}", "{\"endDate\":\"2026-09-01\"}"})
    void 잘못된_수정이나_위치_일정을_거절한다(String json) {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(),
                LocalDate.of(2026, 9, 11), null), requester.getId());
        // when / then
        assertThatThrownBy(() -> update(saved.id(), json)).isInstanceOfSatisfying(CardException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(CardErrorCode.CARD_INVALID_UPDATE));
        assertThat(cards.findById(saved.id()).orElseThrow().getStatus()).isEqualTo(saved.status());
    }

    @Test
    void 다른_프로젝트_보드로_이동하거나_외부인이_수정할_수_없다() {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var other = projects.save(Project.create(creator, "OTHER", "다른 프로젝트", null));
        var target = boards.save(Board.create("외부 보드", other));
        var outsider = users.save(User.create("외부인", "outsider-update@test.com", null));
        // when / then
        assertThatThrownBy(() -> update(saved.id(), "{\"boardId\":" + target.getId() + "}"))
                .isInstanceOf(BoardException.class);
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), board.getId(), saved.id(),
                outsider.getId(), mapper.readValue("{\"status\":\"DONE\"}", CardUpdateRequest.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 존재하지_않거나_경로가_다른_카드와_누락된_요청자를_거절한다() {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var patch = mapper.readValue("{\"status\":\"DONE\"}", CardUpdateRequest.class);
        // when / then
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), board.getId(), saved.id(), requester.getId(), null)).isInstanceOf(CardException.class);
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), board.getId(), saved.id(), null, patch)).isInstanceOf(UserException.class);
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), board.getId(), saved.id(), UUID.randomUUID(), patch)).isInstanceOf(UserException.class);
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), null, saved.id(), requester.getId(), patch)).isInstanceOf(CardException.class);
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), board.getId(), null, requester.getId(), patch)).isInstanceOf(CardException.class);
        assertThatThrownBy(() -> update(Long.MAX_VALUE, "{\"status\":\"DONE\"}")).isInstanceOf(CardException.class);
        assertThatThrownBy(() -> service.update(creator.getId(), "MISSING", board.getId(), saved.id(), requester.getId(), patch)).isInstanceOf(ProjectException.class);
    }

    /**
     * 뒤로 이동할 때 영향 구간만 당기고 같은 트랜잭션의 조회에도 최신 순서를 반환한다.
     */
    @Test
    void 같은_목록에서_뒤로_이동하면_구간만_당기고_즉시_조회에_반영한다() {
        // given
        var first = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var second = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var third = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var fourth = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.IN_PROGRESS);
        // when
        var response = update(second.id(), "{\"sortOrder\":2,\"title\":\"이동한 제목\"}");
        // then
        assertThat(response.sortOrder()).isEqualTo(2);
        assertThat(response.title()).isEqualTo("이동한 제목");
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getId).containsExactly(first.id(), third.id(), second.id(), fourth.id());
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getSortOrder).containsExactly(0, 1, 2, 3);
    }

    /**
     * 연속 이동에서도 벌크 갱신 이전 엔티티의 위치를 재사용하지 않는다.
     */
    @Test
    void 연속_이동은_최신_위치를_사용하고_동일_위치_요청은_유지한다() {
        // given
        var first = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var second = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var third = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        // when
        update(third.id(), "{\"sortOrder\":0}");
        update(first.id(), "{\"sortOrder\":2}");
        update(second.id(), "{\"sortOrder\":1}");
        // then
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getId).containsExactly(third.id(), second.id(), first.id());
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getSortOrder).containsExactly(0, 1, 2);
    }

    /**
     * 다른 보드의 중간 삽입은 양쪽 목록의 위치를 연속되게 정리한다.
     */
    @Test
    void 다른_보드의_중간에_삽입하면_양쪽_순서를_수정한다() {
        // given
        var moving = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var remaining = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        Board target = boards.save(Board.create("대상 보드", project));
        var targetFirst = service.create(creator.getId(), project.getCode(), target.getId(), requester.getId(),
                request(project.getId(), target.getId(), assignee.getId(), null, null));
        var targetSecond = service.create(creator.getId(), project.getCode(), target.getId(), requester.getId(),
                request(project.getId(), target.getId(), assignee.getId(), null, null));
        // when
        var response = update(moving.id(), "{\"boardId\":" + target.getId() + ",\"sortOrder\":1}");
        // then
        assertThat(response.boardId()).isEqualTo(target.getId());
        assertThat(response.sortOrder()).isEqualTo(1);
        assertThat(cards.findById(remaining.id()).orElseThrow().getSortOrder()).isZero();
        assertThat(cards.findAllByBoardIdAndStatus(target.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getId).containsExactly(targetFirst.id(), moving.id(), targetSecond.id());
        assertThat(cards.findAllByBoardIdAndStatus(target.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getSortOrder).containsExactly(0, 1, 2);
    }

    /**
     * 벌크 갱신으로 DB와 미리 조회한 엔티티의 위치를 다르게 만들어 최신 위치 사용을 검증한다.
     */
    @Test
    void 미리_조회한_카드도_DB의_최신_위치로_이동한다() {
        // given
        var first = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var second = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        var third = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        Card cached = cards.findById(third.id()).orElseThrow();
        entityManager.flush();
        entityManager.createQuery("update Card c set c.sortOrder = case when c.id = :cardId then 0 else c.sortOrder + 1 end where c.board.id = :boardId")
                .setParameter("cardId", third.id())
                .setParameter("boardId", board.getId())
                .executeUpdate();
        assertThat(cached.getSortOrder()).isEqualTo(2);
        // when
        var response = update(third.id(), "{\"sortOrder\":2}");
        // then
        assertThat(response.sortOrder()).isEqualTo(2);
        entityManager.clear();
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getId).containsExactly(first.id(), second.id(), third.id());
        assertThat(cards.findAllByBoardIdAndStatus(board.getId(), CardStatus.IN_PROGRESS))
                .extracting(Card::getSortOrder).containsExactly(0, 1, 2);
    }

    /**
     * 조회 뒤 DB에서 다른 보드로 이동한 카드를 이전 경로로 수정하지 못하게 한다.
     */
    @Test
    void 최신_카드의_보드가_경로와_다르면_수정을_거절한다() {
        // given
        var saved = create(request(project.getId(), board.getId(), assignee.getId(), null, null), requester.getId());
        Card cached = cards.findById(saved.id()).orElseThrow();
        Board target = boards.saveAndFlush(Board.create("이동한 보드", project));
        entityManager.createQuery("update Card c set c.board = :board where c.id = :cardId")
                .setParameter("board", target)
                .setParameter("cardId", saved.id())
                .executeUpdate();
        assertThat(cached.getBoard().getId()).isEqualTo(board.getId());
        // when / then
        assertThatThrownBy(() -> update(saved.id(), "{\"title\":\"변경 제목\"}"))
                .isInstanceOfSatisfying(CardException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CardErrorCode.CARD_NOT_FOUND));
        assertThat(cached.getTitle()).isEqualTo(saved.title());
    }

    private CardResponse update(Long cardId, String json) {
        return service.update(creator.getId(), project.getCode(), board.getId(), cardId, requester.getId(),
                mapper.readValue(json, CardUpdateRequest.class));
    }

    private CardResponse create(CardCreateRequest request, UUID requesterId) {
        return service.create(creator.getId(), project.getCode(), board.getId(), requesterId, request);
    }

    private static CardCreateRequest request(Long projectId, Long boardId, Long memberId,
                                             LocalDate startDate, LocalDate endDate) {
        return new CardCreateRequest("홍길동", "로그인 API 구현", "요청 및 응답 구현", CardStatus.IN_PROGRESS,
                memberId, projectId, boardId, startDate, endDate);
    }
}
