package work.managerbe.card.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.board.repository.BoardRepository;
import work.managerbe.card.domain.CardStatus;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.dto.response.CardResponse;
import work.managerbe.card.repository.CardRepository;
import work.managerbe.global.exception.board.BoardException;
import work.managerbe.global.exception.card.CardException;
import work.managerbe.global.exception.member.MemberException;
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
                               BoardRepository boards, MemberRepository members, CardRepository cards) {
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
        members.save(Member.create(requester, project, "MEMBER"));
        assignee = members.save(Member.create(creator, project, "OWNER"));
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
        Member otherMember = members.save(Member.create(requester, other, "MEMBER"));
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

    private CardResponse create(CardCreateRequest request, UUID requesterId) {
        return service.create(creator.getId(), project.getCode(), board.getId(), requesterId, request);
    }

    private static CardCreateRequest request(Long projectId, Long boardId, Long memberId,
                                             LocalDate startDate, LocalDate endDate) {
        return new CardCreateRequest("홍길동", "로그인 API 구현", "요청 및 응답 구현", CardStatus.IN_PROGRESS,
                memberId, projectId, boardId, startDate, endDate);
    }
}
