package work.managerbe.member.service;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import work.managerbe.global.exception.CommonException;
import work.managerbe.global.exception.member.MemberException;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.member.domain.Member;
import work.managerbe.member.dto.*;
import work.managerbe.member.repository.MemberRepository;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 잘못된 입력과 누락된 리소스가 저장소 변경 전에 거절되는지 Mockito로 검증한다. */
class MemberServiceImplTest {
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 7L;
    private final MemberRepository members = mock(MemberRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MemberService service = new MemberServiceImpl(members, projects, users);

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void 잘못된_페이지는_저장소_조회없이_거절한다(int page, int size) {
        // given / when / then
        assertThatThrownBy(() -> service.getAll(OWNER_ID, PROJECT_ID, OWNER_ID, page, size))
                .isInstanceOf(CommonException.class);
        verifyNoInteractions(members, projects, users);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "owner", "UNKNOWN", "OWNER"})
    void 허용하지_않은_역할_수정은_저장소_조회없이_거절한다(String role) {
        // given / when / then
        assertThatThrownBy(() -> service.update(OWNER_ID, PROJECT_ID, OWNER_ID, MEMBER_ID,
                new MemberUpdateRequest(role))).isInstanceOf(MemberException.class);
        verifyNoInteractions(members, projects, users);
    }

    @Test
    void 누락된_요청은_저장소_조회없이_거절한다() {
        // given / when / then
        assertThatThrownBy(() -> service.create(OWNER_ID, PROJECT_ID, OWNER_ID, null))
                .isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> service.create(OWNER_ID, PROJECT_ID, OWNER_ID, new MemberCreateRequest(null, null)))
                .isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> service.update(OWNER_ID, PROJECT_ID, OWNER_ID, MEMBER_ID, null))
                .isInstanceOf(CommonException.class);
        verifyNoInteractions(members, projects, users);
    }

    @Test
    void 누락된_프로젝트_경로는_거절한다() {
        // given / when / then
        assertThatThrownBy(() -> service.get(null, PROJECT_ID, OWNER_ID, MEMBER_ID)).isInstanceOf(ProjectException.class);
        assertThatThrownBy(() -> service.get(OWNER_ID, null, OWNER_ID, MEMBER_ID)).isInstanceOf(ProjectException.class);
        verifyNoInteractions(members, projects, users);
    }

    @Test
    void 없는_프로젝트는_404_예외로_처리한다() {
        // given
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.empty());
        // when / then
        assertThatThrownBy(() -> service.get(OWNER_ID, PROJECT_ID, OWNER_ID, MEMBER_ID)).isInstanceOf(ProjectException.class);
        verifyNoInteractions(members, users);
    }

    @Test
    void 누락된_인증은_멤버_조회전에_거절한다() {
        // given
        project(false);
        // when / then
        assertThatThrownBy(() -> service.get(OWNER_ID, PROJECT_ID, null, MEMBER_ID)).isInstanceOf(CommonException.class);
        verifyNoInteractions(members, users);
    }

    @Test
    void 누락된_멤버_ID는_404_예외로_처리한다() {
        // given
        project(false);
        owner();
        // when / then
        assertThatThrownBy(() -> service.get(OWNER_ID, PROJECT_ID, OWNER_ID, null)).isInstanceOf(MemberException.class);
        verify(members, never()).findByIdAndProject_IdAndLeftAtIsNull(any(), any());
    }

    @Test
    void 없는_사용자_추가는_거절한다() {
        // given
        project(true);
        owner();
        when(users.findById(USER_ID)).thenReturn(Optional.empty());
        // when / then
        assertThatThrownBy(() -> service.create(OWNER_ID, PROJECT_ID, OWNER_ID,
                new MemberCreateRequest(USER_ID, "ADMIN"))).isInstanceOf(UserException.class);
        verify(members, never()).saveAndFlush(any());
    }

    @Test
    void 중복_사용자_추가는_충돌로_처리한다() {
        // given
        project(true);
        owner();
        when(users.findById(USER_ID)).thenReturn(Optional.of(User.create("참여자", "unit@example.com", null)));
        when(members.existsByUser_IdAndProject_Id(USER_ID, PROJECT_ID)).thenReturn(true);
        // when / then
        assertThatThrownBy(() -> service.create(OWNER_ID, PROJECT_ID, OWNER_ID,
                new MemberCreateRequest(USER_ID, "MEMBER"))).isInstanceOf(MemberException.class);
        verify(members, never()).saveAndFlush(any());
    }

    /** 엔티티의 비즈니스 로직 대신 프로젝트 조회 결과만 모킹한다. */
    private void project(boolean lock) {
        User creator = mock(User.class);
        Project project = mock(Project.class);
        when(creator.getId()).thenReturn(OWNER_ID);
        when(project.getCreator()).thenReturn(creator);
        if (lock) {
            when(projects.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        } else {
            when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        }
    }

    /** 권한 검증에 사용할 OWNER 조회 결과를 제공한다. */
    private void owner() {
        Member member = mock(Member.class);
        when(member.getRole()).thenReturn("OWNER");
        when(members.findByUser_IdAndProject_IdAndLeftAtIsNull(OWNER_ID, PROJECT_ID)).thenReturn(Optional.of(member));
    }
}
