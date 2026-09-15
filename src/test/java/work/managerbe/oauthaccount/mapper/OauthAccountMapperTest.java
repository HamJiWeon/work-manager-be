package work.managerbe.oauthaccount.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.managerbe.oauthaccount.domain.OauthAccount;
import work.managerbe.oauthaccount.dto.response.OauthAccountResponse;
import work.managerbe.user.domain.User;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 실제 MapStruct 구현체로 OAuth 계정 응답 변환과 null 입력 처리를 검증한다.
 * 영속성 필드는 Mockito로 반환값을 지정한다.
 */
@ExtendWith(MockitoExtension.class)
class OauthAccountMapperTest {

    @Mock
    OauthAccount oauthAccount;

    @Mock
    User user;

    private final OauthAccountMapper mapper = new OauthAccountMapperImpl();

    @Nested
    @DisplayName("OAuth 계정 응답 변환")
    class ToResponse {

        @Test
        @DisplayName("OAuth 계정의 모든 필드를 응답으로 변환한다.")
        void 전체_필드_변환() {
            // given
            UUID userId = UUID.randomUUID();
            LocalDateTime createdAt = LocalDateTime.parse("2026-09-15T10:00:00");

            when(oauthAccount.getId()).thenReturn(1L);
            when(oauthAccount.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(userId);
            when(oauthAccount.getProvider()).thenReturn("google");
            when(oauthAccount.getProviderUserId()).thenReturn("google-user-123");
            when(oauthAccount.getCreatedAt()).thenReturn(createdAt);

            // when
            OauthAccountResponse response = mapper.toResponse(oauthAccount);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.provider()).isEqualTo("google");
            assertThat(response.providerUserId()).isEqualTo("google-user-123");
            assertThat(response.createdAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("저장 전 사용자의 ID는 null로 변환한다.")
        void 사용자_ID가_없으면_null_반환() {
            // given
            User newUser = User.create("홍길동", "user@example.com", null);
            OauthAccount newAccount =
                    OauthAccount.create(newUser, "google", "google-user-123");

            // when
            OauthAccountResponse response = mapper.toResponse(newAccount);

            // then
            assertThat(response.id()).isNull();
            assertThat(response.userId()).isNull();
            assertThat(response.provider()).isEqualTo("google");
            assertThat(response.providerUserId()).isEqualTo("google-user-123");
            assertThat(response.createdAt()).isNull();
        }

        @Test
        @DisplayName("사용자가 없으면 userId는 null로 변환한다.")
        void 사용자가_없으면_userId_null_반환() {
            // given
            OauthAccount newAccount =
                    OauthAccount.create(null, "google", "google-user-123");

            // when
            OauthAccountResponse response = mapper.toResponse(newAccount);

            // then
            assertThat(response.userId()).isNull();
            assertThat(response.provider()).isEqualTo("google");
            assertThat(response.providerUserId()).isEqualTo("google-user-123");
        }

        @Test
        @DisplayName("OAuth 계정이 null이면 null을 반환한다.")
        void OAuth_계정이_null이면_null_반환() {
            // given
            OauthAccount nullAccount = null;

            // when
            OauthAccountResponse response = mapper.toResponse(nullAccount);

            // then
            assertThat(response).isNull();
        }
    }
}