package work.managerbe.global.base;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 테스트용 하위 타입으로 가입 초기화와 탈퇴 상태 전이를 리플렉션 없이 검증한다.
 */
class JoinEntityTest {

    @Test
    void 최초_초기화에서_가입_시각을_설정한다() {
        // given
        JoinEntity entity = createEntity();
        LocalDateTime before = LocalDateTime.now();

        // when
        entity.initializeJoinedAt();

        // then
        assertThat(entity.getJoinedAt()).isBetween(before, LocalDateTime.now());
        assertThat(entity.getLeftAt()).isNull();
    }

    @Test
    void 반복_초기화해도_최초_가입_시각을_유지한다() {
        // given
        JoinEntity entity = createEntity();
        entity.initializeJoinedAt();
        LocalDateTime joinedAt = entity.getJoinedAt();

        // when
        entity.initializeJoinedAt();

        // then
        assertThat(entity.getJoinedAt()).isSameAs(joinedAt);
    }

    @Test
    void 가입_이후의_탈퇴_시각을_기록한다() {
        // given
        JoinEntity entity = createEntity();
        entity.initializeJoinedAt();
        LocalDateTime leftAt = entity.getJoinedAt().plusSeconds(1);

        // when
        entity.leave(leftAt);

        // then
        assertThat(entity.getLeftAt()).isEqualTo(leftAt);
    }

    @Test
    void 가입과_동일한_시각의_탈퇴를_허용한다() {
        // given
        JoinEntity entity = createEntity();
        entity.initializeJoinedAt();

        // when
        entity.leave(entity.getJoinedAt());

        // then
        assertThat(entity.getLeftAt()).isEqualTo(entity.getJoinedAt());
    }

    @Test
    void 탈퇴_시각이_없으면_거부한다() {
        // given
        JoinEntity entity = createEntity();
        entity.initializeJoinedAt();

        // when / then
        assertThatThrownBy(() -> entity.leave(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("탈퇴 시각은 필수입니다.");
        assertThat(entity.getLeftAt()).isNull();
    }

    @Test
    void 가입_초기화_전의_탈퇴를_거부한다() {
        // given
        JoinEntity entity = createEntity();
        LocalDateTime leftAt = LocalDateTime.now();

        // when / then
        assertThatThrownBy(() -> entity.leave(leftAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("탈퇴 시각은 가입 시각 이후여야 합니다.");
        assertThat(entity.getLeftAt()).isNull();
    }

    @Test
    void 가입보다_이른_탈퇴_시각을_거부한다() {
        // given
        JoinEntity entity = createEntity();
        entity.initializeJoinedAt();
        LocalDateTime leftAt = entity.getJoinedAt().minusNanos(1);

        // when / then
        assertThatThrownBy(() -> entity.leave(leftAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("탈퇴 시각은 가입 시각 이후여야 합니다.");
        assertThat(entity.getLeftAt()).isNull();
    }

    @Test
    void 반복_탈퇴해도_최초_탈퇴_시각을_유지한다() {
        // given
        JoinEntity entity = createEntity();
        entity.initializeJoinedAt();
        LocalDateTime firstLeftAt = entity.getJoinedAt().plusSeconds(1);
        entity.leave(firstLeftAt);

        // when
        entity.leave(firstLeftAt.plusSeconds(1));

        // then
        assertThat(entity.getLeftAt()).isEqualTo(firstLeftAt);
    }

    private static JoinEntity createEntity() {
        return new JoinEntity() { };
    }
}
