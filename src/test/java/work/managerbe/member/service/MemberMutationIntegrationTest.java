package work.managerbe.member.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.card.domain.Card;
import work.managerbe.card.domain.CardStatus;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.service.CardService;
import work.managerbe.global.exception.member.MemberErrorCode;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.member.domain.Member;
import work.managerbe.member.domain.MemberRole;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.member.dto.request.MemberCreateRequest;
import work.managerbe.member.dto.request.MemberUpdateRequest;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 스키마에서 OWNER 권한, 역할 변경, 탈퇴 이력 및 카드 관계 보존을 검증한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:member-mutation-test;DB_CLOSE_DELAY=-1")
@Transactional
class MemberMutationIntegrationTest {
    private final EntityManager entityManager;
    private final MemberService service;
    private final CardService cardService;
    private User creator;
    private User target;
    private Project project;
    private Member owner;
    private Member member;

    @Autowired
    MemberMutationIntegrationTest(EntityManager entityManager, MemberService service, CardService cardService) {
        this.entityManager = entityManager;
        this.service = service;
        this.cardService = cardService;
    }

    @BeforeEach
    void 데이터_준비() {
        creator = User.create("생성자", "mutation-owner@test.com", null);
        target = User.create("대상", "mutation-target@test.com", null);
        entityManager.persist(creator);
        entityManager.persist(target);
        project = Project.create(creator, "MEMBER", "멤버 수정", null);
        entityManager.persist(project);
        owner = Member.create(creator, project, MemberRole.OWNER);
        member = Member.create(target, project, MemberRole.MEMBER);
        entityManager.persist(owner);
        entityManager.persist(member);
        entityManager.flush();
    }

    /** 승격과 강등을 저장하고 가입·생성 시각 유지 및 수정 시각 갱신을 확인한다. */
    @Test
    void OWNER는_멤버를_ADMIN으로_승격하고_MEMBER로_강등한다() {
        // given
        var joinedAt = member.getJoinedAt();
        var createdAt = member.getCreatedAt();
        var updatedAt = member.getUpdatedAt();
        // when
        var promoted = service.update(creator.getId(), project.getCode(), member.getId(), creator.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN));
        var demoted = service.update(creator.getId(), project.getCode(), member.getId(), creator.getId(),
                new MemberUpdateRequest(MemberRole.MEMBER));
        entityManager.clear();
        // then
        assertThat(promoted.role()).isEqualTo(MemberRole.ADMIN);
        assertThat(promoted.updatedAt()).isAfter(updatedAt);
        assertThat(demoted.role()).isEqualTo(MemberRole.MEMBER);
        assertThat(demoted.joinedAt()).isEqualTo(joinedAt);
        assertThat(demoted.createdAt()).isEqualTo(createdAt);
        assertThat(demoted.leftAt()).isNull();
        assertThat(entityManager.find(Member.class, member.getId()).getRole()).isEqualTo(MemberRole.MEMBER);
    }

    @Test
    void 같은_역할_수정은_참여_기록을_유지한다() {
        // given
        var before = MemberResponse.from(member);
        // when
        var response = service.update(creator.getId(), project.getCode(), member.getId(), creator.getId(),
                new MemberUpdateRequest(MemberRole.MEMBER));
        // then
        assertThat(response).isEqualTo(before);
    }

    /** 기존 카드의 담당자를 유지하고 단건·목록에서 탈퇴자를 제외하며 신규 담당자 지정은 거절한다. */
    @Test
    void 탈퇴는_기존_카드와_참여_기록을_보존하고_신규_담당자_지정을_거절한다() {
        // given
        Board board = Board.create("보드", project);
        entityManager.persist(board);
        Card card = Card.create(creator, "소유자", "기존 카드", "내용", CardStatus.NOT_STARTED,
                member, project, board, null, null);
        entityManager.persist(card);
        entityManager.flush();
        var joinedAt = member.getJoinedAt();
        var createdAt = member.getCreatedAt();
        // when
        service.delete(creator.getId(), project.getCode(), member.getId(), creator.getId());
        entityManager.flush();
        var leftAt = member.getLeftAt();
        entityManager.clear();
        // then
        Member saved = entityManager.find(Member.class, member.getId());
        assertThat(saved.getLeftAt()).isNotNull();
        assertThat(saved.getJoinedAt()).isEqualTo(joinedAt);
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(entityManager.find(Card.class, card.getId()).getMember().getId()).isEqualTo(member.getId());
        assertThat(cardService.get(creator.getId(), project.getCode(), board.getId(), card.getId(), creator.getId()).memberId())
                .isEqualTo(member.getId());
        assertThat(service.getAll(creator.getId(), project.getCode(), creator.getId(), 0, 20).items())
                .extracting(MemberResponse::id).containsExactly(owner.getId());
        assertThatThrownBy(() -> service.get(creator.getId(), project.getCode(), member.getId(), creator.getId()))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        assertThatThrownBy(() -> cardService.create(creator.getId(), project.getCode(), board.getId(), creator.getId(),
                new CardCreateRequest("소유자", "새 카드", "내용", CardStatus.NOT_STARTED, member.getId(),
                        project.getId(), board.getId(), null, null)))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        assertThat(leftAt).isAfterOrEqualTo(joinedAt);
    }

    @Test
    void 삭제를_반복해도_최초_탈퇴_시각과_참여_행은_유지된다() {
        // given
        service.delete(creator.getId(), project.getCode(), member.getId(), creator.getId());
        entityManager.flush();
        var leftAt = member.getLeftAt();
        var updatedAt = member.getUpdatedAt();
        // when
        service.delete(creator.getId(), project.getCode(), member.getId(), creator.getId());
        entityManager.flush();
        // then
        assertThat(member.getLeftAt()).isEqualTo(leftAt);
        assertThat(member.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(entityManager.find(Member.class, member.getId())).isNotNull();
    }

    @Test
    void 탈퇴한_멤버의_역할_수정과_재가입은_거절한다() {
        // given
        service.delete(creator.getId(), project.getCode(), member.getId(), creator.getId());
        entityManager.flush();
        // when / then
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), member.getId(), creator.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN)))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        assertThatThrownBy(() -> service.create(creator.getId(), project.getCode(), creator.getId(),
                new MemberCreateRequest(target.getId(), MemberRole.MEMBER)))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_ALREADY_EXISTS));
        assertThatThrownBy(() -> member.changeRole(MemberRole.ADMIN))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void OWNER_부여와_OWNER_자신의_역할_변경_및_삭제는_거절한다() {
        // given / when / then
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), member.getId(), creator.getId(),
                new MemberUpdateRequest(MemberRole.OWNER)))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_INVALID_ROLE));
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), owner.getId(), creator.getId(),
                new MemberUpdateRequest(MemberRole.MEMBER)))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_OWNER_PROTECTED));
        assertThatThrownBy(() -> service.delete(creator.getId(), project.getCode(), owner.getId(), creator.getId()))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_OWNER_PROTECTED));
        assertThat(owner.getRole()).isEqualTo(MemberRole.OWNER);
        assertThat(owner.getLeftAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = MemberRole.class, names = {"ADMIN", "MEMBER"})
    void ADMIN과_MEMBER는_역할_변경이나_자신의_삭제를_할_수_없다(MemberRole role) {
        // given
        member.changeRole(role);
        entityManager.flush();
        // when / then
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), member.getId(), target.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(creator.getId(), project.getCode(), member.getId(), target.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(member.getLeftAt()).isNull();
    }

    @Test
    void 비참여자와_다른_프로젝트의_OWNER는_변경하거나_삭제할_수_없다() {
        // given
        User outsider = User.create("외부", "mutation-outsider@test.com", null);
        User nonMember = User.create("비참여자", "mutation-nonmember@test.com", null);
        entityManager.persist(outsider);
        entityManager.persist(nonMember);
        Project other = Project.create(target, project.getCode(), "같은 코드의 다른 프로젝트", null);
        entityManager.persist(other);
        entityManager.persist(Member.create(outsider, other, MemberRole.OWNER));
        entityManager.flush();
        // when / then
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), member.getId(), outsider.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(creator.getId(), project.getCode(), member.getId(), outsider.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), member.getId(), nonMember.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(creator.getId(), project.getCode(), member.getId(), nonMember.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 탈퇴한_OWNER와_활성_가입이_없는_생성자는_변경하거나_삭제할_수_없다() {
        // given
        owner.leave(owner.getJoinedAt());
        entityManager.flush();
        // when / then
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), member.getId(), creator.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(creator.getId(), project.getCode(), member.getId(), creator.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 다른_프로젝트의_대상과_없는_ID는_멤버_없음으로_거절한다() {
        // given
        Project other = Project.create(target, project.getCode(), "다른 프로젝트", null);
        entityManager.persist(other);
        Member otherMember = Member.create(target, other, MemberRole.MEMBER);
        entityManager.persist(otherMember);
        entityManager.flush();
        // when / then
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), otherMember.getId(), creator.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN)))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        assertThatThrownBy(() -> service.delete(creator.getId(), project.getCode(), otherMember.getId(), creator.getId()))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), null, creator.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN))).isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> service.delete(creator.getId(), project.getCode(), null, creator.getId()))
                .isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> service.delete(creator.getId(), project.getCode(), Long.MAX_VALUE, creator.getId()))
                .isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), Long.MAX_VALUE, creator.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN))).isInstanceOf(MemberException.class);
    }

    @Test
    void 누락된_요청과_역할은_400_오류로_거절한다() {
        // given / when / then
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), member.getId(), creator.getId(), null))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_INVALID_ROLE));
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), member.getId(), creator.getId(),
                new MemberUpdateRequest(null))).isInstanceOf(MemberException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "MISSING"})
    void 유효하지_않은_프로젝트_코드는_수정과_삭제에서_거절한다(String code) {
        // given / when / then
        assertThatThrownBy(() -> service.update(creator.getId(), code, member.getId(), creator.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN))).isInstanceOf(ProjectException.class);
        assertThatThrownBy(() -> service.delete(creator.getId(), code, member.getId(), creator.getId()))
                .isInstanceOf(ProjectException.class);
    }

    @Test
    void 요청자나_생성자가_없으면_수정과_삭제에서_거절한다() {
        // given / when / then
        assertThatThrownBy(() -> service.update(creator.getId(), project.getCode(), member.getId(), null,
                new MemberUpdateRequest(MemberRole.ADMIN))).isInstanceOf(UserException.class);
        assertThatThrownBy(() -> service.delete(creator.getId(), project.getCode(), member.getId(), UUID.randomUUID()))
                .isInstanceOf(UserException.class);
        assertThatThrownBy(() -> service.update(null, project.getCode(), member.getId(), creator.getId(),
                new MemberUpdateRequest(MemberRole.ADMIN))).isInstanceOf(ProjectException.class);
        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), project.getCode(), member.getId(), creator.getId()))
                .isInstanceOf(ProjectException.class);
    }
}
