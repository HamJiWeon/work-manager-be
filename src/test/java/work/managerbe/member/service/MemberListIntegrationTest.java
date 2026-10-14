package work.managerbe.member.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.member.domain.Member;
import work.managerbe.member.domain.MemberRole;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.project.domain.Project;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 스키마에서 활성 멤버 필터, 정렬, 페이지 집계 및 프로젝트별 조회 권한을 검증한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:member-list-test;DB_CLOSE_DELAY=-1")
@Transactional
class MemberListIntegrationTest {
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 9, 14, 9, 0);

    private final MemberService service;
    private final EntityManager entityManager;
    private User creator;
    private Project project;
    private Member owner;

    @Autowired
    MemberListIntegrationTest(MemberService service, EntityManager entityManager) {
        this.service = service;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void 데이터_준비() {
        creator = User.create("생성자", "member-list-owner@test.com", null);
        entityManager.persist(creator);
        project = Project.create(creator, "MEMBERS", "멤버 목록", null);
        entityManager.persist(project);
        owner = Member.create(creator, project, MemberRole.OWNER);
        entityManager.persist(owner);
        entityManager.flush();
    }

    /** ID 순서와 다른 가입 시각 및 동일 시각을 저장해 두 정렬 키와 페이지 경계를 검증한다. */
    @Test
    void 활성_멤버만_가입_시각과_ID_오름차순으로_페이지_조회하고_집계한다() {
        // given
        Member later = saveMember("later", MemberRole.MEMBER);
        Member sameTime = saveMember("same-time", MemberRole.ADMIN);
        Member left = saveMember("left", MemberRole.MEMBER);
        left.leave(left.getJoinedAt().plusSeconds(1));
        Project other = Project.create(creator, "OTHER", "다른 프로젝트", null);
        entityManager.persist(other);
        entityManager.persist(Member.create(creator, other, MemberRole.OWNER));
        setJoinedAt(owner, JOINED_AT);
        setJoinedAt(sameTime, JOINED_AT);
        setJoinedAt(later, JOINED_AT.plusDays(1));
        entityManager.clear();
        // when
        var first = service.getAll(creator.getId(), project.getCode(), creator.getId(), 0, 2);
        var second = service.getAll(creator.getId(), project.getCode(), creator.getId(), 1, 2);
        // then
        assertThat(first.items()).extracting(MemberResponse::id).containsExactly(owner.getId(), sameTime.getId());
        assertThat(first.items()).extracting(MemberResponse::role).containsExactly(MemberRole.OWNER, MemberRole.ADMIN);
        assertThat(first.items()).extracting(MemberResponse::joinedAt).containsExactly(JOINED_AT, JOINED_AT);
        assertThat(first.items()).extracting(MemberResponse::leftAt).containsOnlyNulls();
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.items()).extracting(MemberResponse::id).containsExactly(later.getId());
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.totalElements()).isEqualTo(3);
        assertThat(second.totalPages()).isEqualTo(2);
    }

    @Test
    void 마지막_페이지_이후에는_빈_목록과_전체_집계를_반환한다() {
        // given / when
        var response = service.getAll(creator.getId(), project.getCode(), creator.getId(), 1, 20);
        // then
        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(MemberRole.class)
    void 활성_요청자는_역할에_관계없이_목록을_조회한다(MemberRole role) {
        // given
        Member requester = saveMember("reader", role);
        // when
        var response = service.getAll(creator.getId(), project.getCode(), requester.getUser().getId(), 0, 20);
        // then
        assertThat(response.items()).extracting(MemberResponse::id).containsExactly(owner.getId(), requester.getId());
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(MemberRole.class)
    void 탈퇴한_요청자는_역할에_관계없이_목록을_조회할_수_없다(MemberRole role) {
        // given
        Member requester = saveMember("left-reader", role);
        requester.leave(requester.getJoinedAt().plusSeconds(1));
        entityManager.flush();
        // when / then
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), requester.getUser().getId(), 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 비참여자와_다른_프로젝트의_관리자는_목록을_조회할_수_없다() {
        // given
        User outsider = User.create("비참여자", "member-list-outsider@test.com", null);
        User otherAdmin = User.create("다른 관리자", "member-list-other-admin@test.com", null);
        entityManager.persist(outsider);
        entityManager.persist(otherAdmin);
        Project other = Project.create(otherAdmin, project.getCode(), "같은 코드의 다른 프로젝트", null);
        entityManager.persist(other);
        entityManager.persist(Member.create(otherAdmin, other, MemberRole.ADMIN));
        entityManager.flush();
        // when / then
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), outsider.getId(), 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), otherAdmin.getId(), 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 생성자도_활성_가입이_없으면_목록을_조회할_수_없다() {
        // given
        entityManager.remove(owner);
        entityManager.flush();
        // when / then
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), creator.getId(), 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 요청자가_없으면_사용자_없음으로_처리한다() {
        // given / when / then
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), null, 0, 20))
                .isInstanceOfSatisfying(UserException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
        assertThatThrownBy(() -> service.getAll(creator.getId(), project.getCode(), UUID.randomUUID(), 0, 20))
                .isInstanceOfSatisfying(UserException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "MISSING"})
    void 유효하지_않은_프로젝트_코드는_프로젝트_없음으로_처리한다(String code) {
        // given / when / then
        assertThatThrownBy(() -> service.getAll(creator.getId(), code, creator.getId(), 0, 20))
                .isInstanceOfSatisfying(ProjectException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void 생성자_ID가_없거나_일치하지_않으면_프로젝트_없음으로_처리한다() {
        // given / when / then
        assertThatThrownBy(() -> service.getAll(null, project.getCode(), creator.getId(), 0, 20))
                .isInstanceOf(ProjectException.class);
        assertThatThrownBy(() -> service.getAll(UUID.randomUUID(), project.getCode(), creator.getId(), 0, 20))
                .isInstanceOf(ProjectException.class);
    }

    /** 조회 검증에 필요한 사용자와 프로젝트 멤버를 저장한다. */
    private Member saveMember(String name, MemberRole role) {
        User user = User.create(name, "member-list-" + name + "@test.com", null);
        entityManager.persist(user);
        Member member = Member.create(user, project, role);
        entityManager.persist(member);
        entityManager.flush();
        return member;
    }

    /** 리플렉션 없이 DB 준비 데이터를 갱신해 가입 시각 정렬과 동일 시각의 ID 정렬을 검증한다. */
    private void setJoinedAt(Member member, LocalDateTime joinedAt) {
        entityManager.flush();
        entityManager.createNativeQuery("update members set joined_at = :joinedAt where id = :id")
                .setParameter("joinedAt", joinedAt).setParameter("id", member.getId()).executeUpdate();
    }
}
