package work.managerbe.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.user.domain.User;
import work.managerbe.user.dto.response.UserResponse;
import work.managerbe.user.mapper.UserMapper;
import work.managerbe.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    UserRepository userRepository;

    @Mock
    UserMapper userMapper;

    @InjectMocks
    UserServiceImpl userService;

    /**
     * principal의 UUID로 사용자를 조회하고 매핑한 응답을 반환하는지 검증한다.
     */
    @Test
    void 내부_UUID로_현재_사용자를_조회한다() {
        // given
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        LocalDateTime now = LocalDateTime.of(2026, 9, 24, 10, 0);
        UserResponse response = new UserResponse(
                userId, "홍길동", "user@example.com", null, now, now);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        // when
        UserResponse result = userService.getMe(userId);

        // then
        assertThat(result).isSameAs(response);
        verify(userRepository).findById(userId);
        verify(userMapper).toResponse(user);
    }

    /**
     * principal의 UUID와 일치하는 사용자가 없으면 USER_NOT_FOUND를 반환하는지 검증한다.
     */
    @Test
    void 내부_UUID에_해당하는_사용자가_없으면_예외를_던진다() {
        // given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getMe(userId))
                .isInstanceOf(UserException.class)
                .satisfies(exception -> assertThat(((UserException) exception).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));

        verify(userRepository).findById(userId);
        verifyNoInteractions(userMapper);
    }
}
