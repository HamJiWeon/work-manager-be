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
import work.managerbe.card.dto.response.CardResponse;
import work.managerbe.card.repository.CardRepository;
import work.managerbe.global.exception.board.BoardException;
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
        project = projects.save(Project.create(creator, "CARD", "프로젝트", null));
        members.save(Member.create(requester, project, MemberRole.MEMBER));
        assignee = members.save(Member.create(creator, project, MemberRole.OWNER));
        board = boards.save(Board.create("보드", project));
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

    /** 뒤로 이동할 때 영향 구간만 당기고 같은 트랜잭션의 조회에도 최신 순서를 반환한다. */
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

    /** 연속 이동에서도 벌크 갱신 이전 엔티티의 위치를 재사용하지 않는다. */
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

    /** 다른 보드의 중간 삽입은 양쪽 목록의 위치를 연속되게 정리한다. */
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

    /** 벌크 갱신으로 DB와 미리 조회한 엔티티의 위치를 다르게 만들어 최신 위치 사용을 검증한다. */
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

    /** 조회 뒤 DB에서 다른 보드로 이동한 카드를 이전 경로로 수정하지 못하게 한다. */
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
