package work.managerbe.user.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.managerbe.user.domain.User;
import work.managerbe.user.dto.response.UserResponse;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 실제 MapStruct 구현체로 사용자 응답 변환과 null 입력 처리를 검증한다.
 * 영속성 필드는 Mockito로 반환값을 지정한다.
 */
@ExtendWith(MockitoExtension.class)
class UserMapperTest {

    @Mock
    User user;

    private final UserMapper mapper = new UserMapperImpl();

    @Nested
    @DisplayName("사용자 응답 변환")
    class ToResponse {

        @Test
        @DisplayName("사용자의 모든 필드를 응답으로 변환한다.")
        void 전체_필드_변환() {
            // given
            UUID userId = UUID.randomUUID();
            LocalDateTime createdAt = LocalDateTime.parse("2026-09-15T10:00:00");
            LocalDateTime updatedAt = LocalDateTime.parse("2026-09-15T11:00:00");

            when(user.getId()).thenReturn(userId);
            when(user.getName()).thenReturn("홍길동");
            when(user.getEmail()).thenReturn("user@example.com");
            when(user.getProfileImgUrl()).thenReturn("https://example.com/profile.png");
            when(user.getCreatedAt()).thenReturn(createdAt);
            when(user.getUpdatedAt()).thenReturn(updatedAt);

            // when
            UserResponse response = mapper.toResponse(user);

            // then
            assertThat(response.id()).isEqualTo(userId);
            assertThat(response.name()).isEqualTo("홍길동");
            assertThat(response.email()).isEqualTo("user@example.com");
            assertThat(response.profileImgUrl()).isEqualTo("https://example.com/profile.png");
            assertThat(response.createdAt()).isEqualTo(createdAt);
            assertThat(response.updatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("선택 정보가 없는 새 사용자를 응답으로 변환한다.")
        void 선택_정보가_없는_사용자_변환() {
            // given
            User newUser = User.create("홍길동", null, null);

            // when
            UserResponse response = mapper.toResponse(newUser);

            // then
            assertThat(response.name()).isEqualTo("홍길동");
            assertThat(response.id()).isNull();
            assertThat(response.email()).isNull();
            assertThat(response.profileImgUrl()).isNull();
            assertThat(response.createdAt()).isNull();
            assertThat(response.updatedAt()).isNull();
        }

        @Test
        @DisplayName("사용자가 null이면 null을 반환한다.")
        void 사용자가_null이면_null_반환() {
            // given
            User nullUser = null;

            // when
            UserResponse response = mapper.toResponse(nullUser);

            // then
            assertThat(response).isNull();
        }
    }
}