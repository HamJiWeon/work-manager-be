package work.managerbe.member.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.card.domain.Card;
import work.managerbe.global.exception.member.*;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.member.domain.Member;
import work.managerbe.member.dto.*;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.*;

/** 실제 스키마와 트랜잭션으로 활성 조회, 역할 권한, 탈퇴 및 카드 관계 보존을 검증한다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-service-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Transactional
class MemberServiceIntegrationTest {
    private final MemberService service;
    private final EntityManager entityManager;
    private User owner;
    private User user;
    private Project project;
    private Member ownerMember;
    private Member member;

    @Autowired
    MemberServiceIntegrationTest(MemberService service, EntityManager entityManager) {
        this.service = service;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void 참여_데이터를_저장한다() {
        owner = User.create("소유자", "owner-member@example.com", null);
        user = User.create("참여자", "participant@example.com", null);
        entityManager.persist(owner);
        entityManager.persist(user);
        project = Project.create(owner, "MEMBERS", "멤버 테스트", null);
        entityManager.persist(project);
        ownerMember = Member.create(owner, project, "OWNER");
        member = Member.create(user, project, "MEMBER");
        entityManager.persist(ownerMember);
        entityManager.persist(member);
        entityManager.flush();
    }

    @Test
    void 추가는_기본_역할과_감사시각을_반환한다() {
        // given
        User newUser = User.create("새 사용자", "new-member@example.com", null);
        entityManager.persist(newUser);
        // when
        var response = service.create(owner.getId(), project.getId(), owner.getId(),
                new MemberCreateRequest(newUser.getId(), null));
        // then
        assertThat(response.id()).isNotNull();
        assertThat(response.userId()).isEqualTo(newUser.getId());
        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.role()).isEqualTo("MEMBER");
        assertThat(response.joinedAt()).isNotNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.leftAt()).isNull();
    }

    @Test
    void 활성_참여자는_단건과_페이지를_조회한다() {
        // given
        entityManager.createNativeQuery("UPDATE members SET joined_at = :time WHERE project_id = :projectId")
                .setParameter("time", LocalDateTime.of(2025, 1, 1, 0, 0))
                .setParameter("projectId", project.getId()).executeUpdate();
        entityManager.clear();
        // when
        var response = service.get(owner.getId(), project.getId(), user.getId(), member.getId());
        var page = service.getAll(owner.getId(), project.getId(), user.getId(), 0, 1);
        var next = service.getAll(owner.getId(), project.getId(), user.getId(), 1, 1);
        // then
        assertThat(response.id()).isEqualTo(member.getId());
        assertThat(page.items()).extracting(MemberResponse::id).containsExactly(ownerMember.getId());
        assertThat(next.items()).extracting(MemberResponse::id).containsExactly(member.getId());
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(next.page()).isEqualTo(1);
        assertThat(next.size()).isEqualTo(1);
    }

    @Test
    void 역할_수정은_가입시각을_보존하고_수정시각을_갱신한다() {
        // given
        var joinedAt = member.getJoinedAt();
        var oldTime = LocalDateTime.of(2025, 1, 1, 0, 0);
        entityManager.createNativeQuery("UPDATE members SET updated_at = :time WHERE id = :id")
                .setParameter("time", oldTime).setParameter("id", member.getId()).executeUpdate();
        entityManager.clear();
        // when
        var response = service.update(owner.getId(), project.getId(), owner.getId(), member.getId(),
                new MemberUpdateRequest("ADMIN"));
        entityManager.clear();
        // then
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.joinedAt()).isCloseTo(joinedAt, within(1, ChronoUnit.MICROS));
        assertThat(response.updatedAt()).isAfter(oldTime);
        assertThat(entityManager.find(Member.class, member.getId()).getRole()).isEqualTo("ADMIN");
    }

    @Test
    void 탈퇴는_기존_카드_관계를_보존하고_신규_지정과_조회와_재가입을_거절한다() {
        // given
        Board board = project.addBoard("보드");
        entityManager.persist(board);
        entityManager.flush();
        entityManager.createNativeQuery("""
                INSERT INTO cards (user_id, member_id, project_id, board_id, title, content, created_at, updated_at)
                VALUES (:userId, :memberId, :projectId, :boardId, '카드', '내용', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)
                .setParameter("userId", user.getId()).setParameter("memberId", member.getId())
                .setParameter("projectId", project.getId()).setParameter("boardId", board.getId()).executeUpdate();
        // when
        service.delete(owner.getId(), project.getId(), owner.getId(), member.getId());
        entityManager.clear();
        // then
        Member departed = entityManager.find(Member.class, member.getId());
        assertThat(departed.getLeftAt()).isNotNull();
        assertThat(departed.getJoinedAt()).isCloseTo(member.getJoinedAt(), within(1, ChronoUnit.MICROS));
        Card card = entityManager.createQuery("select c from Card c where c.project.id = :id", Card.class)
                .setParameter("id", project.getId()).getSingleResult();
        assertThat(card.getMember().getId()).isEqualTo(member.getId());
        assertThat(service.getAll(owner.getId(), project.getId(), owner.getId(), 0, 20).totalElements()).isEqualTo(1);
        assertThatThrownBy(() -> Card.create("참여자", "새 카드", null, departed, project, board, null, null))
                .isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> Card.builder().member(departed).build()).isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> service.get(owner.getId(), project.getId(), owner.getId(), member.getId()))
                .isInstanceOf(MemberException.class)
                .satisfies(exception -> assertThat(((MemberException) exception).getErrorCode())
                        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        assertThatThrownBy(() -> service.create(owner.getId(), project.getId(), owner.getId(),
                new MemberCreateRequest(user.getId(), null))).isInstanceOf(MemberException.class)
                .satisfies(exception -> assertThat(((MemberException) exception).getErrorCode())
                        .isEqualTo(MemberErrorCode.MEMBER_DUPLICATE));
    }

    @Test
    void 일반_멤버는_변경_권한이_없다() {
        // given / when / then
        assertThatThrownBy(() -> service.update(owner.getId(), project.getId(), user.getId(), member.getId(),
                new MemberUpdateRequest("ADMIN"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(owner.getId(), project.getId(), user.getId(), member.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(owner.getId(), project.getId(), user.getId(),
                new MemberCreateRequest(user.getId(), null))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 소유자의_강등과_삭제와_일반_멤버의_승격을_거절한다() {
        // given / when / then
        assertThatThrownBy(() -> service.update(owner.getId(), project.getId(), owner.getId(), ownerMember.getId(),
                new MemberUpdateRequest("ADMIN"))).isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> service.delete(owner.getId(), project.getId(), owner.getId(), ownerMember.getId()))
                .isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> service.update(owner.getId(), project.getId(), owner.getId(), member.getId(),
                new MemberUpdateRequest("OWNER"))).isInstanceOf(MemberException.class);
    }

    @Test
    void 비참여자와_탈퇴한_요청자의_조회는_거절한다() {
        // given
        member.leave(LocalDateTime.now());
        entityManager.flush();
        // when / then
        assertThatThrownBy(() -> service.get(owner.getId(), project.getId(), user.getId(), ownerMember.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAll(owner.getId(), project.getId(), java.util.UUID.randomUUID(), 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 경로_생성자가_다르거나_다른_프로젝트의_멤버면_조회되지_않는다() {
        // given
        Project other = Project.create(owner, "OTHER", "다른 프로젝트", null);
        entityManager.persist(other);
        Member otherMember = Member.create(user, other, "MEMBER");
        entityManager.persist(otherMember);
        entityManager.flush();
        // when / then
        assertThatThrownBy(() -> service.get(user.getId(), project.getId(), owner.getId(), member.getId()))
                .isInstanceOf(ProjectException.class);
        assertThatThrownBy(() -> service.get(owner.getId(), project.getId(), owner.getId(), otherMember.getId()))
                .isInstanceOf(MemberException.class);
    }
}
