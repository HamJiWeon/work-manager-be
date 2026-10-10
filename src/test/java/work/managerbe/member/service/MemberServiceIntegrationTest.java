package work.managerbe.member.service;

import work.managerbe.member.domain.MemberRole;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.exception.member.MemberErrorCode;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.member.domain.Member;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.member.dto.request.MemberCreateRequest;
import work.managerbe.member.repository.MemberRepository;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;

/** 실제 스키마에서 멤버 추가, 권한, 중복 가입 및 입력 검증을 확인한다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:member-create-test;DB_CLOSE_DELAY=-1")
@Transactional
class MemberServiceIntegrationTest {
    private final MemberService service;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final MemberRepository members;
    private User creator;
    private User target;
    private Project project;

    @Autowired
    MemberServiceIntegrationTest(MemberService service, UserRepository users,
                                 ProjectRepository projects, MemberRepository members) {
        this.service = service;
        this.users = users;
        this.projects = projects;
        this.members = members;
    }

    @BeforeEach
    void 데이터_준비() {
        creator = users.save(User.create("생성자", "member-owner@test.com", null));
        target = users.save(User.create("대상", "member-target@test.com", null));
        project = projects.save(Project.create(creator, "MEMBER", "프로젝트", null));
        members.saveAndFlush(Member.create(creator, project, MemberRole.OWNER));
    }

    @Test
    void 역할을_생략하면_일반_멤버로_추가하고_가입과_감사_시각을_반환한다() {
        // given
        var request = new MemberCreateRequest(target.getId(), null);
        // when
        var response = service.create(creator.getId(), project.getCode(), creator.getId(), request);
        // then
        var saved = members.findById(response.id()).orElseThrow();
        assertThat(response.userId()).isEqualTo(target.getId());
        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.role()).isEqualTo(MemberRole.MEMBER);
        assertThat(response.joinedAt()).isNotNull();
        assertThat(response.leftAt()).isNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(saved.getUser().getId()).isEqualTo(target.getId());
        assertThat(saved.getProject().getId()).isEqualTo(project.getId());
    }

    @Test
    void 이미_가입한_사용자는_중복_오류로_거절한다() {
        // given
        members.saveAndFlush(Member.create(target, project, MemberRole.MEMBER));
        // when / then
        assertThatThrownBy(() -> add(target.getId())).isInstanceOfSatisfying(MemberException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_ALREADY_EXISTS));
        assertThat(members.count()).isEqualTo(2);
    }

    @Test
    void 탈퇴한_사용자도_기존_가입_이력을_보존하고_중복으로_거절한다() {
        // given
        Member previous = members.saveAndFlush(Member.create(target, project, MemberRole.MEMBER));
        previous.leave(LocalDateTime.now().plusSeconds(1));
        members.flush();
        // when / then
        assertThatThrownBy(() -> add(target.getId())).isInstanceOfSatisfying(MemberException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_ALREADY_EXISTS));
        assertThat(previous.getLeftAt()).isNotNull();
        assertThat(members.count()).isEqualTo(2);
    }

    @Test
    void 다른_프로젝트에_가입한_사용자는_추가할_수_있다() {
        // given
        Project other = projects.save(Project.create(creator, "OTHER", "다른 프로젝트", null));
        members.saveAndFlush(Member.create(target, other, MemberRole.MEMBER));
        // when
        var response = add(target.getId());
        // then
        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(members.count()).isEqualTo(3);
    }

    @Test
    void 활성_일반_멤버는_추가할_수_없다() {
        // given
        members.saveAndFlush(Member.create(target, project, MemberRole.MEMBER));
        var request = new MemberCreateRequest(creator.getId(), MemberRole.MEMBER);
        // when / then
        assertThatThrownBy(() -> service.create(creator.getId(), project.getCode(), target.getId(), request))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** 생성자 ID와 요청자 ID가 달라도 프로젝트 관리자 역할로 멤버를 추가한다. */
    @ParameterizedTest
    @EnumSource(value = MemberRole.class, names = {"OWNER", "ADMIN"})
    void 활성_OWNER와_ADMIN은_멤버를_추가할_수_있다(MemberRole role) {
        // given
        User manager = users.save(User.create("관리자", "member-manager@test.com", null));
        members.saveAndFlush(Member.create(manager, project, role));
        var request = new MemberCreateRequest(target.getId(), MemberRole.MEMBER);
        // when
        var response = service.create(creator.getId(), project.getCode(), manager.getId(), request);
        // then
        assertThat(response.userId()).isEqualTo(target.getId());
        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.role()).isEqualTo(MemberRole.MEMBER);
    }

    /** 역할이 관리자여도 탈퇴한 프로젝트에서는 멤버 추가를 거절한다. */
    @ParameterizedTest
    @EnumSource(value = MemberRole.class, names = {"OWNER", "ADMIN"})
    void 탈퇴한_OWNER와_ADMIN은_멤버를_추가할_수_없다(MemberRole role) {
        // given
        User manager = users.save(User.create("탈퇴 관리자", "member-left-manager@test.com", null));
        Member membership = members.saveAndFlush(Member.create(manager, project, role));
        membership.leave(LocalDateTime.now().plusSeconds(1));
        members.flush();
        var request = new MemberCreateRequest(target.getId(), MemberRole.MEMBER);
        // when / then
        assertThatThrownBy(() -> service.create(creator.getId(), project.getCode(), manager.getId(), request))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(members.existsByUser_IdAndProject_Id(target.getId(), project.getId())).isFalse();
    }

    @Test
    void 다른_프로젝트의_ADMIN과_비참여자는_추가할_수_없다() {
        // given
        User manager = users.save(User.create("외부 관리자", "member-other-manager@test.com", null));
        Project other = projects.save(Project.create(creator, "OTHER", "다른 프로젝트", null));
        members.saveAndFlush(Member.create(manager, other, MemberRole.ADMIN));
        var request = new MemberCreateRequest(target.getId(), MemberRole.MEMBER);
        // when / then
        assertThatThrownBy(() -> service.create(creator.getId(), project.getCode(), manager.getId(), request))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(creator.getId(), project.getCode(), target.getId(), request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 생성자도_활성_OWNER_가입이_없으면_추가할_수_없다() {
        // given
        members.deleteAll();
        members.flush();
        // when / then
        assertThatThrownBy(() -> add(target.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = MemberRole.class, names = {"OWNER", "ADMIN"})
    void 허용되지_않은_역할은_거절한다(MemberRole role) {
        // given
        var request = new MemberCreateRequest(target.getId(), role);
        // when / then
        assertThatThrownBy(() -> service.create(creator.getId(), project.getCode(), creator.getId(), request))
                .isInstanceOfSatisfying(MemberException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_INVALID_REQUEST));
        assertThat(members.count()).isEqualTo(1);
    }

    @Test
    void 요청이나_대상_ID가_없으면_거절한다() {
        // given / when / then
        assertThatThrownBy(() -> service.create(creator.getId(), project.getCode(), creator.getId(), null))
                .isInstanceOf(MemberException.class);
        assertThatThrownBy(() -> add(null)).isInstanceOf(MemberException.class);
    }

    @Test
    void 요청자나_대상_사용자가_없으면_거절한다() {
        // given
        var request = new MemberCreateRequest(target.getId(), MemberRole.MEMBER);
        // when / then
        assertThatThrownBy(() -> service.create(creator.getId(), project.getCode(), null, request))
                .isInstanceOf(UserException.class);
        assertThatThrownBy(() -> service.create(creator.getId(), project.getCode(), UUID.randomUUID(), request))
                .isInstanceOf(UserException.class);
        assertThatThrownBy(() -> add(UUID.randomUUID())).isInstanceOf(UserException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "MISSING"})
    void 프로젝트_코드가_잘못되면_거절한다(String code) {
        // given
        var request = new MemberCreateRequest(target.getId(), MemberRole.MEMBER);
        // when / then
        assertThatThrownBy(() -> service.create(creator.getId(), code, creator.getId(), request))
                .isInstanceOf(ProjectException.class);
    }

    @Test
    void 프로젝트_생성자_ID가_없으면_거절한다() {
        // given
        var request = new MemberCreateRequest(target.getId(), MemberRole.MEMBER);
        // when / then
        assertThatThrownBy(() -> service.create(null, project.getCode(), creator.getId(), request))
                .isInstanceOf(ProjectException.class);
    }

    private MemberResponse add(UUID userId) {
        return service.create(creator.getId(), project.getCode(), creator.getId(),
                new MemberCreateRequest(userId, MemberRole.MEMBER));
    }
}
